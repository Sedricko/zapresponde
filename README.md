# ZapResponde

App Android próprio de respostas automáticas para WhatsApp e WhatsApp Business, com regras no app e na planilha do Google Sheets.

## Instalar

1. Abra a aba **Actions** deste repositório, entre na última execução de "Build APK" com sucesso e baixe o arquivo **ZapResponde-apk**.
2. Descompacte e instale o `app-debug.apk` no celular (permita "instalar apps desconhecidos").
3. Abra o app, toque em **Liberar acesso** e ative o ZapResponde no acesso às notificações.
4. Ligue **Respostas automáticas** na tela inicial. Use **Testar** para simular mensagens.

## Conectar a planilha

1. Crie uma planilha no Google Sheets e abra **Extensões > Apps Script**. Apague o que estiver lá, cole o conteúdo de [`apps-script/Codigo.gs`](apps-script/Codigo.gs) e salve. Não precisa editar nada.
2. **Implantar > Nova implantação**, tipo **App da Web**. Executar como: **Eu**. Quem pode acessar: **Qualquer pessoa**. Autorize quando o Google pedir.
3. Recarregue a planilha. No menu **ZapResponde > Conectar celular** aparece um QR code (as abas Respostas e Consulta são criadas nessa hora).
4. No app: **Planilha > Ler QR code**. O app conecta, testa e sincroniza sozinho.

A chave de acesso é gerada e guardada pelo próprio script. Para trocar, use **ZapResponde > Gerar nova chave** e leia o QR de novo.

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
