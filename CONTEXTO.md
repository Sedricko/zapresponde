# ZapResponde: contexto para continuar o trabalho

Atualizado em 18/10/2026, app 0.9.13 e script 0.9.3. Cole este arquivo, ou peça para ler `CONTEXTO.md` do repositório, ao começar uma conversa nova.

## O que é
App Android próprio (Kotlin, escrito do zero) que responde ao WhatsApp e ao WhatsApp Business automaticamente, lendo as notificações. Ele se integra ao Google Sheets por um Apps Script colado na planilha. Dono: William (Transportes Poloni, cargas e fretes). A comunicação é sempre em português.

## Regras do William (seguir sempre)
1. **Sugerir primeiro e só fazer depois que ele aprovar.** Não criar funções por conta própria. Bugs podem ser corrigidos direto.
2. Toda função tem botão de ligar e desligar em Ajustes, mais uma tela de configuração. Funções novas vêm desligadas.
3. Visual moderno e configuração simples, no estilo WhatAuto e AutoResponder.
4. O script da planilha sempre tem número de versão: no comentário da linha 1, na constante `VERSAO`, no item de menu "Versão do script" e no retorno `versao` do doGet. Troque o número a cada mudança.
5. Nunca copiar, reempacotar ou crackear código ou recursos do WhatAuto ou do AutoResponder. Eles servem só de referência de funções.
6. "Você" e "Meu" no WhatsApp dele são o outro número particular dele, um contato normal. Não tratar como especial.

## Onde está
- Repositório: https://github.com/Sedricko/zapresponde (privado, branch `main`). O app do Claude no GitHub tem acesso.
- APK: cada push roda o GitHub Actions (`.github/workflows`, gradle 8.11.1, Java 17, `gradle testDebugUnitTest assembleDebug`) e gera o artifact **ZapResponde-apk**. Envie ao William o link `https://github.com/Sedricko/zapresponde/actions/runs/<id>`.
- Assinatura fixa (`app/zapresponde.keystore`, alias e senha `zapresponde`): as atualizações instalam por cima.
- O ambiente na nuvem bloqueia dl.google.com, maven.google.com, script.google.com e o download de artifacts. Por isso o APK sai pelo CI. Os testes JVM rodam localmente com um projeto gradle à parte, só com Model.kt, Engine.kt e EngineTest.kt.
- Versão do app: `app/build.gradle.kts` (versionCode e versionName). Suba a cada entrega.
- Arquivos do projeto em `/mnt/project-files/zapresponde/`: `Codigo.gs` (script atual), `TUTORIAL-ZapResponde-0.9.5.md` (manual do usuário, substitui o guia PDF 0.9.2), `skill-zapresponde/SKILL.md`, `PACOTE-0.9.5.md` e o zip com tudo para abrir em outra IA.

## Arquitetura (app/src/main/java/app/zapresponde)
- **Health.kt:** rastro de conexão/desconexão do listener e das notificações; `report()` para o Diagnóstico.
- **Assistant.kt:** Assistente de IA (Gemini): `prompt`, `parse`, `problems`, `check`, `gemini`.
- **Model.kt:** enums Match, Who, Groups, Freq, ReplyMode, Kind e Audience. Classes Rule, Config (todos os botões), ChatState, State (com lastBackup), Incoming (com fromMe) e Decision (replies, rule, delayMs, formRow, reason, leadRow, alert).
- **Engine.kt:** lógica pura com testes. `decide()` segue esta ordem:
  1. robô ligado
  2. fromMe (sua mensagem na conversa)
  3. filtro de contatos (`audience`)
  4. agenda
  5. pausa
  6. eco da nossa própria resposta
  7. proteção contra loop
  8. pedido pela conversa
  9. **Fretes** (`freteRoute`/`freteMessage`)
  10. boas-vindas
  11. regras (a primeira que servir)

  Outras funções: `matches` (Contém casa palavra inteira, com `hasWord`), `lookup` (até 5 linhas empatadas, modelo com {A}..{Z}), `fill` (variáveis), `rulesFromSheet` (colunas A a F), `freteSelfTest`, `parseConnectQr`.
- **ReplyService.kt:** NotificationListenerService.
  - Faz o parse da notificação. MessagingStyle sem sender conta como fromMe e pausa a conversa.
  - Deduplica e lê a planilha na hora quando a mensagem é do FreteBras.
  - `answer` → modo aprovação ou `deliver`, que envia pelo RemoteInput, grava o histórico e enfileira Historico, Pedidos e Leads.
  - `Store.log` grava o registro usado pelo Diagnóstico.
  - Takeover: tocar na notificação ou escrever na conversa (fromMe) pausa por takeoverMin. Notificação sumir por leitura (inclusive WhatsApp Web) só pausa se takeoverOnRead estiver ligado; vem desligado desde a 0.9.3, porque o William usa o WhatsApp Web.
- **Store.kt:** estado em JSON no filesDir (config, rules, sheet, history, events, state, outbox). Backup em export/import.
- **Sheets.kt:** web app do Apps Script. `read` faz GET ?key=. `append` faz POST e depois GET no Location. `readBackup` usa ?backup=1. `refresh`, `scriptVersion`. O SyncWorker (WorkManager, mínimo de 15 min) envia a fila e faz o backup diário.
- **Alerts.kt:** canal "alertas", alerta de carga e notificação de aprovação com Enviar e Descartar (ApprovalReceiver).
- **Contacts.kt:** READ_CONTACTS, nomes da agenda (cache de 10 min).
- **ui/MainActivity.kt:** abas Início, Regras, Planilha, Histórico e Ajustes, com telas de detalhe. Ajustes tem Respostas, Comportamento e Ferramentas (Assistente de IA, Testar respostas, Backup, Diagnóstico). **ui/Widgets.kt** e **ui/Theme.kt** (verde #128C5E).
- **apps-script/Codigo.gs:**
  - Menu ZapResponde: Conectar celular (QR), Ligar planilha de cargas, Gerar nova chave, Versão do script.
  - `doGet` devolve Respostas, Consulta (ou a primeira aba da planilha de cargas da propriedade CARGAS) e `versao`, ou o Backup com ?backup=1.
  - `doPost` insere linhas embaixo do cabeçalho, as mais novas em cima.
  - `criarAbas` cria as listas de escolha nas colunas C a F.

## Planilhas do William
- **ZapResponde:** tem o script. Web app implantado, link https://script.google.com/macros/s/AKfycbyuZxfIA00Tj5LHN8yicSC_-gpbLDVzjOJPRuEMbRM_uOsSNP0enEm19RZIw-1mtJ48HQ/exec. Para atualizar o código: Implantar > Gerenciar implantações > lápis > **Nova versão**.
- **Cargas disponiveis** (https://docs.google.com/spreadsheets/d/1dVvlFq3eqowV7wx-b3mY1HpiczCEgNXc_na-u_bVCv4), aba Página1, gerada pelo script "Gestão de Cargas" dele.
  - Linha 1: status. Coluna A: rota "Ponta Grossa/PR para Rio de Janeiro/RJ". Coluna B: mensagem pronta do WhatsApp (ORIGEM, DESTINO, CLIENTE, VALOR, CARGA, VEÍCULO, PROGRAMAÇÃO, Ref).
  - Também tem linhas de comando: "Lista" e "Gerar Lista", com várias cargas na coluna B.
  - O ZapResponde só lê essa planilha. Atenção: em 17/10 o William colou o script do ZapResponde no projeto "Macros gravadas" dessa planilha por engano. Pedi para ele remover e confirmar se o código "Gestão de Cargas" não foi apagado. Até agora não confirmou.

## Funções prontas (todas com botão em Ajustes)
Regras (8 tipos de comparação, variáveis, frequência, horários, contatos e grupos por regra, consulta à planilha), Boas-vindas, Pedido pela conversa (aba Pedidos), Cabeçalho e rodapé, Atraso humano, Agenda, Pausa quando você assume, Proteção contra loop, Contatos (estilo WhatAuto com seletor da agenda), **Fretes FreteBras** (rota pela frase ou pelo link, mensagem da coluna B, ou "FRETE INDISPONÍVEL" editável com o link do grupo), Aba Leads, Alerta de carga pedida, Modo aprovação, Backup (arquivo e diário na planilha), Testar respostas, **Diagnóstico** (relatório completo, causa mais provável e últimas 15 mensagens com o motivo), **Assistente de IA** e Histórico.

## Estado e pendências (18/10/2026)
- 0.9.4 (aguardando teste no celular): `Health.kt` guarda conexão/desconexão do serviço e últimas notificações; o serviço pede reconexão sozinho; Diagnóstico mostra aparelho, recursos, erro de sincronização e "CAUSA MAIS PROVÁVEL"; Início mostra "Ligado, sem conexão"; motivo detalhado quando nenhuma regra responde; sincronização com trava única; frete lê a planilha com timeout de 8 s. Script 0.9.3: só grava nas abas do app e como texto puro.
- 0.9.5: **Assistente de IA** (Ajustes > Ferramentas, vem desligado). `Assistant.kt` manda o pedido + esquema da `Rule` ao Gemini (`gemini-flash-latest`, chave em `Config.aiKey`, fora do backup), lê a regra, confere valores, testa os exemplos com `Engine.matches` e só grava quando o William toca em Aplicar. Pedidos seguintes ajustam a regra atual. Outros provedores (Claude, OpenAI) ficam para depois.
- 0.9.6: Assistente tenta de novo e troca de modelo sozinho (gemini-flash-latest, flash-lite-latest, 2.5-flash, 2.5-flash-lite) em erro 5xx, 429 ou 404. O William recebeu 503 (Gemini sobrecarregado) no primeiro teste.
- 0.9.7: **Frete escrito de qualquer jeito** (Ajustes > Fretes, `freteLivreOn`, desligado): `Engine.freteLivre` acha na mensagem as cidades das rotas da planilha de cargas (acento e maiúscula ignorados, UF opcional com / ou -, nome composto inteiro, **sem tolerar erro de digitação**, pedido do William). 1ª cidade = origem, 2ª = destino; uma cidade só depois de "para/pra" lista cargas para ela, e com "carga/frete" também as que saem dela. Até 3 mensagens; sem carga usa freteNotFound.
- 0.9.8: frete escrito de qualquer jeito ignora anúncio de carga (texto com ORIGEM: e DESTINO:). "Você" e "Meu" no mesmo celular respondiam um ao outro com os anúncios até a proteção contra loop parar.
- 0.9.9: **caminho da decisão** (`Decision.trace`, guardado em `LogEntry.trace`): cada passo do `decide` (filtros, fretes, cada regra que casou ou não e por quê). O Diagnóstico mostra o caminho das 5 últimas mensagens. **Diagnóstico pela IA** (`aiDiagOn`, desligado; Ajustes > Ferramentas e tela Diagnóstico): manda relatório + ajustes + regras ao Gemini (`Assistant.diagnosisPrompt`, sem aiKey, sheetUrl e sheetKey; texto das mensagens vai junto) e mostra a explicação.
- 0.9.10: fretes (FreteBras e texto livre) mandam **uma carga só**, a de coleta mais próxima (`Engine.nearestFirst`/`loadDate`, lê "Coleta: dd/mm hh:mm"; de hoje em diante primeiro, depois as passadas, por último sem data). Pedido do William: a lista de até 3 poluía a conversa. Eco: mensagem igual a qualquer resposta nossa recente, em qualquer conversa, é ignorada (a resposta ao Johon voltava como mensagem do "Você"). Gemini: timeout de 120 s, e no timeout tenta o próximo modelo.
- 0.9.11: **Testar regras com a IA** (Assistente de IA, embaixo; usa aiOn): escolhe as regras ativas (todas marcadas), o Gemini cria mensagens e diz qual regra deveria responder (`rulesTestPrompt`/`parseRuleTests`), o app confere pela ordem real com `Engine.matches` (`runRuleTests`) e mostra o que não bateu + observações da IA. Só as regras vão ao Gemini.
- 0.9.12 (correções): carga com "FRETE INDISPONÍVEL" na mensagem (`Engine.closed`) conta como encerrada: Leads "indisponível", sem alerta, vai por último na escolha. `Engine.keepLoads`: leitura da planilha com só as linhas de status (o script "Gestão de Cargas" faz clearContents + setValues a cada minuto) mantém a última cópia boa.
- Script "Gestão de Cargas" v9.2 (aba Página1, confirmado em 18/10, código intacto): linhas 1-2 status; cargas ativas "Cidade/UF para Cidade/UF" (ou "A ➔ B ➔ C" na troca de nota Unilever) + mensagem com "Coleta: Qui 22/10 18:00" ou "A combinar"; linha "Lista" (resumo para motorista); linha "Gerar Lista" (texto cru para publicar em outra plataforma, NÃO é para motorista); cargas fechadas/canceladas das últimas 48h com o texto fixo "❌ *FRETE INDISPONÍVEL*". Roda a cada 1 min (0h-5h a cada 55 min) e ao marcar FECHADO na aba Mot.
- 0.9.13: **Acompanhamento** (Ajustes > Respostas, `followOn`, desligado): depois de conversa respondida pelo app (não grupo), `ChatState.followAt` = última mensagem + `followMin` (1 a 1440); nova mensagem do contato recomeça; mensagem sua (não eco) ou conversa pausada cancela; frases `followJoined` ("já estou no grupo"...) marcam `followDone`. `ReplyService.followCheck` (a cada 1 min) envia `followText` com {link} = `followLink` pelo último botão Responder da conversa (`lastReply`, só em memória: reinício do app perde os pendentes, registrado no Diagnóstico). Uma vez por contato. Usa o filtro de Contatos global (o app inteiro segue um filtro só). O William pediu detecção automática de quem já está nos grupos: impossível (sem API; grupos só admin escreve); combinado envio único + frases.
- Estado de uma resposta só é devolvido se o envio falhar ou se você descartar a aprovação, nunca no cancelamento por mensagem nova (isso apagava a pausa de quando você assume). "Respondido" só vai para o registro depois do envio.
- Testado no celular: respostas, boas-vindas, regras, gravação do Historico na planilha e Fretes (a primeira mensagem FreteBras foi respondida). A planilha de cargas está ligada, com 164 linhas.
- Último problema: dois anúncios só com link ficaram sem resposta. A causa provável é o WhatsApp Web aberto (sem notificação no celular) ou a conversa pausada. Pedi para ele rodar o Diagnóstico e mandar o relatório.
- **Sugestões aguardando aprovação:**
  1. (FEITO na 0.9.7) Responder mensagens fora do padrão FreteBras ("Goiania/GO para Aparecida de Goiania", "Sao paulo pra Serra", "tem carga de Goiânia pra Rio Verde?"): aceitar "X para/pra Y" com ou sem UF e acento, reconhecer as cidades pelas rotas da planilha de cargas, listar até 3 cargas quando houver só destino ou ambiguidade, e "FRETE INDISPONÍVEL" quando não achar. Botão em Ajustes > Fretes, desligado. O William pediu exemplos e gostou; falta o "sim" final.
  2. "Comandos da planilha": texto da coluna A que não é rota vira comando exato (ex.: "Lista"); ignorar linhas que começam com "Gerar". Explicado ao William com a planilha real; aguardando o "sim".
- Fase 2 do plano original, ainda não feita: menu de atendimento, follow-up e ficha de cliente (CRM). Plano: https://claude.ai/code/artifact/6f406ff7-3639-4dba-b99c-71bc37ae628c

## Como continuar
1. Clone o repo, leia este arquivo e o README.
2. Faça a mudança, rode os testes JVM do Engine, suba versionCode e versionName (e `VERSAO` se mexer no script), faça commit e push, espere o CI ficar verde e mande o link do run.
3. Se mudar o script: copie para `/mnt/project-files/zapresponde/Codigo.gs` e lembre o William de implantar como Nova versão na planilha **ZapResponde**.
