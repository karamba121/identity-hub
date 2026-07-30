# ADR-001: Monólito modular como unidade inicial de implantação

- **Status:** aceito
- **Data:** 2026-07-30

## Contexto

O Identity Hub possui capacidades distintas: identidades, tenancy, controle de
acesso, clientes OAuth, protocolos, chaves, auditoria e administração. Esses
limites são úteis para modelagem e evolução, mas não criam, por si só, uma
necessidade de processos e bancos independentes.

Autenticação e emissão de tokens exigem consistência e baixa latência entre
várias dessas capacidades. Começar distribuído adicionaria falhas de rede,
contratos remotos, observabilidade distribuída e consistência eventual antes de
existir evidência de escala ou autonomia que compense esse custo.

## Decisão

O backend começará como um monólito modular Spring Boot:

- uma unidade de implantação;
- um pipeline;
- um banco PostgreSQL, com propriedade de tabelas explícita por módulo;
- módulos com APIs internas e dependências direcionadas;
- testes arquiteturais para proteger limites relevantes;
- comunicação em processo por padrão.

Módulo lógico, bounded context e unidade física de implantação não serão
tratados como sinônimos.

Eventos de domínio poderão desacoplar efeitos dentro da aplicação. Mensageria e
Outbox serão adotadas somente para integrações assíncronas reais que precisem de
entrega durável.

## Consequências

- transações críticas podem permanecer locais;
- desenvolvimento e operação iniciais são mais simples;
- limites precisam ser defendidos por código e testes, não pela rede;
- uma consulta não pode ignorar propriedade de dados apenas porque compartilha
  o mesmo banco;
- extrações futuras exigirão contratos, migração de dados e tratamento de
  falhas distribuídas;
- escala independente não estará disponível até que uma necessidade justifique
  a extração.

## Alternativas consideradas

### Microsserviço por capacidade

Rejeitado no início por aumentar custo operacional e superfície de falha sem
evidência de benefício.

### Aplicação sem módulos explícitos

Rejeitada porque facilitaria acoplamento entre credenciais, protocolo,
administração e autorização.

## Evidências exigidas

- regras de dependência verificadas automaticamente;
- módulo sem acesso direto às tabelas internas de outro módulo;
- testes das transações críticas dentro de uma única unidade de trabalho;
- documentação de qualquer exceção de dependência;
- dados operacionais antes de propor extração física.
