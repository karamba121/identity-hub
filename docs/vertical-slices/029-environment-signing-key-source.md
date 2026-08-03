# Fatia vertical 029 — origem de chave por ambiente

- **Estado:** entregue
- **Data:** 2026-08-03
- **ADRs exercitadas:** 002, 005 e 007

## Capacidade entregue

A criação do `JWKSource` deixa de gerar chaves diretamente em `SecurityConfig`.
O novo contrato `SigningKeyProvider` separa a origem do material criptográfico
dos consumidores de assinatura, decodificação local e publicação do JWK Set.

Duas fontes são selecionáveis por configuração:

- `generated`: RSA efêmera de 2048 bits e `kid` aleatório, mantida como padrão
  compatível para desenvolvimento e testes;
- `pem`: chave privada PKCS#8 e pública X.509 montadas como recursos externos
  `file:`, com `kid` explícito ou derivado do thumbprint público.

## Falha fechada

O modo PEM interrompe a inicialização quando os arquivos estão ausentes,
ilegíveis, excessivamente grandes, em formato inesperado, abaixo de 2048 bits
ou pertencem a pares distintos. A correspondência é verificada tanto pelo
módulo RSA quanto por assinatura e verificação de um desafio em memória.

Não existe fallback de PEM para geração efêmera. Dessa forma, uma montagem
incorreta não inicia o issuer com uma chave imprevista e não invalida tokens de
forma silenciosa.

## Evidências executadas

- fonte gerada produz chave privada/publicável e `kid` diferente a cada carga;
- o mesmo par PEM produz chave pública e `kid` estáveis entre cargas;
- `key-id` operacional explícito é preservado;
- par divergente e RSA de 1024 bits são rejeitados;
- a fábrica seleciona a implementação indicada pelo ambiente;
- contexto completo do Authorization Server inicia com o modo padrão;
- suíte completa do backend, testes do resource server, build Angular e
  validação do Compose verificados ao concluir a fatia.

## Limites ainda abertos

- o modo PEM publica somente uma chave e não implementa rotação;
- chaves anteriores não permanecem no JWK Set;
- não houve inicialização de container com um secret PEM realmente montado;
- HSM, KMS, Vault e PEM cifrado não são fontes implementadas nesta fatia;
- os arquivos de teste são criados no `target` do módulo porque a limpeza do
  diretório temporário do JUnit recebeu `AccessDeniedException` no Windows.
