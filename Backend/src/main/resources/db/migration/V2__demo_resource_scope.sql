UPDATE oauth2_registered_client
SET scopes = scopes || ',demo.read'
WHERE client_id = 'identity-hub-demo'
  AND (',' || scopes || ',') NOT LIKE '%,demo.read,%';
