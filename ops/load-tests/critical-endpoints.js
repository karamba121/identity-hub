import http from 'k6/http';
import exec from 'k6/execution';
import { check } from 'k6';
import { Rate } from 'k6/metrics';

const BASE_URL = (__ENV.IDENTITY_HUB_LOAD_TEST_BASE_URL || 'http://backend:8080').replace(/\/$/, '');
const DURATION = duration(__ENV.IDENTITY_HUB_LOAD_TEST_DURATION || '30s');
const READ_RATE = positiveInteger(__ENV.IDENTITY_HUB_LOAD_TEST_READ_RATE || '25', 'READ_RATE');
const AUTHORIZATION_RATE = positiveInteger(
  __ENV.IDENTITY_HUB_LOAD_TEST_AUTHORIZATION_RATE || '5',
  'AUTHORIZATION_RATE',
);
const CONTRACT_FAILURES = new Rate('identity_hub_load_test_contract_failures');

export const options = {
  discardResponseBodies: true,
  scenarios: {
    protocol_reads: {
      executor: 'constant-arrival-rate',
      exec: 'protocolReads',
      rate: READ_RATE,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: Math.max(5, Math.ceil(READ_RATE / 2)),
      maxVUs: Math.max(20, READ_RATE * 2),
      tags: { workload: 'protocol_reads' },
    },
    authorization_starts: {
      executor: 'constant-arrival-rate',
      exec: 'authorizationStarts',
      rate: AUTHORIZATION_RATE,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: Math.max(3, AUTHORIZATION_RATE),
      maxVUs: Math.max(10, AUTHORIZATION_RATE * 3),
      tags: { workload: 'authorization_starts' },
    },
  },
  thresholds: {
    checks: ['rate>0.995'],
    identity_hub_load_test_contract_failures: ['rate<0.005'],
    'http_req_failed{workload:protocol_reads}': ['rate<0.01'],
    'http_req_failed{workload:authorization_starts}': ['rate<0.01'],
    'http_req_duration{endpoint:openid_configuration}': ['p(95)<500', 'p(99)<1000'],
    'http_req_duration{endpoint:jwk_set}': ['p(95)<500', 'p(99)<1000'],
    'http_req_duration{endpoint:authorization_start}': ['p(95)<750', 'p(99)<1500'],
    dropped_iterations: ['count==0'],
  },
};

export function setup() {
  const readiness = http.get(`${BASE_URL}/actuator/health/readiness`, {
    responseType: 'text',
    tags: { endpoint: 'readiness_probe', name: 'GET /actuator/health/readiness' },
  });
  const ready = check(readiness, {
    'backend pronto antes da carga': (response) => response.status === 200,
  });
  if (!ready) {
    throw new Error(`Backend indisponível para o teste de carga: HTTP ${readiness.status}`);
  }
}

export function protocolReads() {
  const responses = http.batch([
    ['GET', `${BASE_URL}/.well-known/openid-configuration`, null, {
      tags: { endpoint: 'openid_configuration', name: 'GET /.well-known/openid-configuration' },
    }],
    ['GET', `${BASE_URL}/oauth2/jwks`, null, {
      tags: { endpoint: 'jwk_set', name: 'GET /oauth2/jwks' },
    }],
  ]);

  const valid = check(responses, {
    'discovery responde 200': (result) => result[0].status === 200,
    'JWK Set responde 200': (result) => result[1].status === 200,
  });
  CONTRACT_FAILURES.add(!valid);
}

export function authorizationStarts() {
  const sequence = `${exec.vu.idInTest}-${exec.scenario.iterationInTest}`;
  const query = [
    'response_type=code',
    'client_id=identity-hub-demo',
    `redirect_uri=${encodeURIComponent('http://localhost:4200/demo/callback')}`,
    `scope=${encodeURIComponent('openid profile email demo.read')}`,
    `state=load-${sequence}`,
    `nonce=load-${sequence}`,
    `code_challenge=${'a'.repeat(43)}`,
    'code_challenge_method=S256',
  ].join('&');
  const response = http.get(`${BASE_URL}/oauth2/authorize?${query}`, {
    redirects: 0,
    responseType: 'text',
    responseCallback: http.expectedStatuses(302),
    tags: { endpoint: 'authorization_start', name: 'GET /oauth2/authorize' },
  });
  const valid = check(response, {
    'autorização cria interação opaca': (result) => result.status === 302
      && (result.headers.Location || '').includes('/signin?interaction_id='),
  });
  CONTRACT_FAILURES.add(!valid);
}

function positiveInteger(raw, name) {
  const parsed = Number(raw);
  if (!Number.isInteger(parsed) || parsed <= 0) {
    throw new Error(`IDENTITY_HUB_LOAD_TEST_${name} deve ser um inteiro positivo`);
  }
  return parsed;
}

function duration(raw) {
  if (!/^\d+(ms|s|m|h)$/.test(raw)) {
    throw new Error('IDENTITY_HUB_LOAD_TEST_DURATION deve usar ms, s, m ou h');
  }
  return raw;
}
