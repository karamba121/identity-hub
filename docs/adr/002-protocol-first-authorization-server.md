# ADR-002: Servidor de autorização orientado por padrões

- **Status:** aceito
- **Data:** 2026-07-30

## Contexto

Um endpoint que valida senha e gera JWT não constitui um provedor de identidade.
OAuth e OpenID Connect definem papéis, endpoints, metadados, validações,
respostas de erro e propriedades de segurança que são fáceis de implementar
incorretamente em código próprio.

O projeto precisa demonstrar customização empresarial sem assumir a manutenção
de um protocolo proprietário.

## Decisão

Spring Security e Spring Authorization Server formarão a base dos endpoints de
protocolo. Customizações usarão seus pontos de extensão suportados.

A linha de base será:

- OAuth 2.0 e OpenID Connect 1.0;
- recomendações do OAuth 2.0 Security Best Current Practice;
- perfil alinhado à evolução OAuth 2.1;
- Authorization Code com PKCE para acesso de usuário;
- Client Credentials para clientes confidenciais autorizados;
- discovery metadata, JWK Set e UserInfo;
- revogação e demais endpoints apenas quando seus contratos e semânticas
  estiverem definidos.

Implicit grant e Resource Owner Password Credentials não serão suportados.
Extensões entram por necessidade documentada e threat model, não por quantidade
de funcionalidades.

## Consequências

- grande parte da conformidade fica apoiada em componentes especializados;
- atualizações da biblioteca e dos padrões exigem acompanhamento;
- páginas de login, consentimento, claims e tenancy ainda exigem integração
  cuidadosa;
- testes devem verificar comportamento de protocolo, não apenas métodos
  internos;
- versões são fixadas e registradas junto a cada incremento executável; a
  primeira fatia usa Spring Boot 4.0.7, Spring Security Authorization Server
  7.0.6 e Java 17.

## Alternativas consideradas

### Implementação integral própria

Rejeitada devido ao risco de falhas sutis, custo de conformidade e manutenção.

### API local de login e JWT

Rejeitada porque não atende discovery, consentimento, tipos de cliente,
revogação, federação nem interoperabilidade OIDC.

### Produto externo sem código do domínio

Não adotado como núcleo deste portfólio, pois esconderia justamente as decisões
de integração e segurança que o projeto deve demonstrar.

## Evidências exigidas

- testes de conformidade e integração para endpoints habilitados;
- metadata consistente com os endpoints realmente disponíveis;
- respostas de erro sem detalhes sensíveis;
- testes negativos para grants desabilitados;
- registro da versão e documentação oficial usadas em cada incremento.
