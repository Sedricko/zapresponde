# ZapResponde

App Android próprio de respostas automáticas para WhatsApp e WhatsApp Business, com regras no app e na planilha do Google Sheets.

## Instalar

1. Abra a aba **Actions** deste repositório, entre na última execução de "Build APK" com sucesso e baixe o arquivo **ZapResponde-apk**.
2. Descompacte e instale o `app-debug.apk` no celular (permita "instalar apps desconhecidos").
3. Abra o app, toque em **Liberar acesso** e ative o ZapResponde no acesso às notificações.
4. Ligue **Respostas automáticas** na tela inicial. Use **Testar** para simular mensagens.

## Conectar a planilha

1. Crie uma planilha no Google Sheets.
2. Extensões > Apps Script. Apague o que estiver lá e cole o conteúdo de [`apps-script/Codigo.gs`](apps-script/Codigo.gs).
3. Troque `TROQUE-ESTA-CHAVE` por uma senha sua.
4. Rode a função `criarAbas` uma vez (cria as abas Respostas e Consulta com exemplo). Autorize quando pedir.
5. Implantar > Nova implantação > tipo **App da Web**. Executar como: **Eu**. Quem pode acessar: **Qualquer pessoa**. Copie o link que termina em `/exec`.
6. No app: Google Sheets > cole o link e a senha > ligue "Usar planilha" > Sincronizar agora.

Se mudar o código do script depois, faça Implantar > Gerenciar implantações > editar > Nova versão, senão o link continua com o código antigo.

### Abas

| Aba | Colunas | Quem escreve |
| --- | --- | --- |
| Respostas | A palavras-chave (vírgula) · B resposta (várias com `\|\|` são sorteadas) · C comparação (contém, exata, começa, termina, curinga, regex, parecida, todas) · D quem (todos, contatos, grupos) · E ativo (sim/não) | Você |
| Consulta | Qualquer tabela. Uma regra do tipo "Consulta na planilha" diz a coluna de busca e a de resposta | Você |
| Pedidos | Data, conversa e uma coluna por pergunta | O app |
| Historico | Data, app, conversa, tipo, mensagem, resposta, regra | O app |

A primeira linha de cada aba é o cabeçalho e é ignorada.

## Variáveis nas respostas

`{nome}` `{primeiro_nome}` `{mensagem}` `{data}` `{hora}` `{dia_semana}` `{saudacao}` `{grupo}` `{contador}`

## Limites

- Só responde quando o WhatsApp mostra notificação (não com a conversa aberta ou silenciada).
- Só texto, e só para quem mandou mensagem.
- Automatizar o WhatsApp comum não é permitido pelos termos do WhatsApp; volume alto pode levar a bloqueio do número.

## Desenvolvimento

Kotlin + Jetpack Compose. A lógica de decisão fica em `Engine.kt` (sem dependências do Android) e é testada em `EngineTest.kt`. O GitHub Actions roda os testes e gera o APK a cada push.
