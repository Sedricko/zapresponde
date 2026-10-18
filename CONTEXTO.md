# ZapResponde: contexto para continuar o trabalho

Atualizado em 18/10/2026, app 0.9.5 e script 0.9.3. Cole este arquivo, ou peça para ler `CONTEXTO.md` do repositório, ao começar uma conversa nova.

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
- Arquivos do projeto: `/mnt/project-files/zapresponde/Codigo.gs` (script atual) e `Guia-ZapResponde-0.9.2.pdf` (manual do usuário).

## Arquitetura (app/src/main/java/app/zapresponde)
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
- **ui/MainActivity.kt:** abas Início, Regras, Planilha, Histórico e Ajustes, com telas de detalhe. Ajustes tem Respostas, Comportamento e Ferramentas (Testar respostas, Backup, Diagnóstico). **ui/Widgets.kt** e **ui/Theme.kt** (verde #128C5E).
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
Regras (8 tipos de comparação, variáveis, frequência, horários, contatos e grupos por regra, consulta à planilha), Boas-vindas, Pedido pela conversa (aba Pedidos), Cabeçalho e rodapé, Atraso humano, Agenda, Pausa quando você assume, Proteção contra loop, Contatos (estilo WhatAuto com seletor da agenda), **Fretes FreteBras** (rota pela frase ou pelo link, mensagem da coluna B, ou "FRETE INDISPONÍVEL" editável com o link do grupo), Aba Leads, Alerta de carga pedida, Modo aprovação, Backup (arquivo e diário na planilha), Testar respostas, **Diagnóstico** (relatório completo e últimas 15 mensagens com o motivo) e Histórico.

## Estado e pendências (18/10/2026)
- 0.9.4 (aguardando teste no celular): `Health.kt` guarda conexão/desconexão do serviço e últimas notificações; o serviço pede reconexão sozinho; Diagnóstico mostra aparelho, recursos, erro de sincronização e "CAUSA MAIS PROVÁVEL"; Início mostra "Ligado, sem conexão"; motivo detalhado quando nenhuma regra responde; sincronização com trava única; frete lê a planilha com timeout de 8 s. Script 0.9.3: só grava nas abas do app e como texto puro.
- 0.9.5: **Assistente de IA** (Ajustes > Ferramentas, vem desligado). `Assistant.kt` manda o pedido + esquema da `Rule` ao Gemini (`gemini-flash-latest`, chave em `Config.aiKey`, fora do backup), lê a regra, confere valores, testa os exemplos com `Engine.matches` e só grava quando o William toca em Aplicar. Pedidos seguintes ajustam a regra atual. Outros provedores (Claude, OpenAI) ficam para depois.
- Estado de uma resposta só é devolvido se o envio falhar ou se você descartar a aprovação, nunca no cancelamento por mensagem nova (isso apagava a pausa de quando você assume). "Respondido" só vai para o registro depois do envio.
- Testado no celular: respostas, boas-vindas, regras, gravação do Historico na planilha e Fretes (a primeira mensagem FreteBras foi respondida). A planilha de cargas está ligada, com 164 linhas.
- Último problema: dois anúncios só com link ficaram sem resposta. A causa provável é o WhatsApp Web aberto (sem notificação no celular) ou a conversa pausada. Pedi para ele rodar o Diagnóstico e mandar o relatório.
- **Sugestões aguardando aprovação:**
  1. Responder mensagens fora do padrão FreteBras ("tem carga de Goiânia pra Rio Verde?"): achar origem e destino da planilha no texto, e só destino lista até 3 cargas.
  2. "Comandos da planilha": texto da coluna A que não é rota vira comando exato (ex.: "Lista").
- Fase 2 do plano original, ainda não feita: menu de atendimento, follow-up e ficha de cliente (CRM). Plano: https://claude.ai/code/artifact/6f406ff7-3639-4dba-b99c-71bc37ae628c

## Como continuar
1. Clone o repo, leia este arquivo e o README.
2. Faça a mudança, rode os testes JVM do Engine, suba versionCode e versionName (e `VERSAO` se mexer no script), faça commit e push, espere o CI ficar verde e mande o link do run.
3. Se mudar o script: copie para `/mnt/project-files/zapresponde/Codigo.gs` e lembre o William de implantar como Nova versão na planilha **ZapResponde**.
