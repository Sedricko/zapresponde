// ZapResponde - script da planilha, versão 0.9.2
// ZapResponde: cole este código em Extensões > Apps Script da sua planilha e salve.
// Depois: Implantar > Nova implantação > App da Web (executar como "Eu", acesso "Qualquer pessoa").
// Por fim, recarregue a planilha e use o menu ZapResponde > Conectar celular. Não precisa editar nada aqui.

const VERSAO = '0.9.2'; // aparece no menu da planilha e na janela do QR; atualizar a cada mudança

function chave() {
  const props = PropertiesService.getScriptProperties();
  let k = props.getProperty('CHAVE');
  if (!k) {
    k = Utilities.getUuid();
    props.setProperty('CHAVE', k);
  }
  return k;
}

function onOpen() {
  SpreadsheetApp.getUi().createMenu('ZapResponde')
    .addItem('Conectar celular', 'conectar')
    .addItem('Ligar planilha de cargas', 'ligarCargas')
    .addItem('Gerar nova chave', 'novaChave')
    .addSeparator()
    .addItem('Versão do script: ' + VERSAO, 'versao')
    .addToUi();
}

function versao() {
  SpreadsheetApp.getUi().alert('Script ZapResponde versão ' + VERSAO);
}

// Cria as abas que faltam e mostra o QR code que o app lê.
function conectar() {
  criarAbas();
  const url = ScriptApp.getService().getUrl() || '';
  const t = HtmlService.createTemplate(HTML);
  t.qrUrl = url;
  t.qrKey = chave();
  let ui;
  try {
    ui = SpreadsheetApp.getUi();
  } catch (err) {
    // Run from the editor while the spreadsheet tab is closed: the dialog has nowhere to appear.
    console.log('Link: ' + (url || '(implante primeiro)') + '\nChave: ' + chave());
    throw new Error('Abra a planilha em outra aba, recarregue e use o menu ZapResponde > Conectar celular. (O link e a chave também estão no registro acima, para digitar no app.)');
  }
  ui.showModalDialog(t.evaluate().setWidth(400).setHeight(660), 'Conectar o ZapResponde');
}

// Called by the dialog: checks the link answers like the app will see it. Empty string = OK.
function testarLink(url) {
  if (!/^https:\/\/script\.google\.com\/.*\/exec$/.test(url)) return 'O link precisa começar com https://script.google.com/ e terminar em /exec.';
  const r = UrlFetchApp.fetch(url + '?key=' + encodeURIComponent(chave()), { muteHttpExceptions: true, followRedirects: true });
  const code = r.getResponseCode();
  const body = r.getContentText();
  if (code === 200 && body.indexOf('"Respostas"') >= 0) return '';
  if (code === 404) return 'Esse link não existe (erro 404). Copie o link certo em Implantar > Gerenciar implantações > App da Web.';
  if (body.indexOf('chave incorreta') >= 0) return 'Essa implantação está com o código antigo. Em Implantar > Gerenciar implantações, clique no lápis, escolha Nova versão e Implantar.';
  if (code === 200) return 'O link pediu login. Na implantação, o acesso precisa ser "Qualquer pessoa".';
  return 'O link respondeu com erro ' + code + '.';
}

function novaChave() {
  PropertiesService.getScriptProperties().deleteProperty('CHAVE');
  chave();
  conectar();
}

// Faz a aba Consulta do app vir da primeira aba de outra planilha (ex.: Cargas disponíveis).
function ligarCargas() {
  const ui = SpreadsheetApp.getUi();
  const props = PropertiesService.getScriptProperties();
  const atual = props.getProperty('CARGAS') || '';
  const r = ui.prompt('Planilha de cargas', 'Cole o link da planilha (a primeira aba será usada no lugar da aba Consulta).' +
    (atual ? '\nAtual: ' + atual + '\nDeixe vazio para voltar a usar a aba Consulta.' : ''), ui.ButtonSet.OK_CANCEL);
  if (r.getSelectedButton() !== ui.Button.OK) return;
  const link = r.getResponseText().trim();
  if (!link) { props.deleteProperty('CARGAS'); ui.alert('Pronto: o app volta a usar a aba Consulta.'); return; }
  let aba;
  try { aba = SpreadsheetApp.openByUrl(link).getSheets()[0]; } catch (err) { ui.alert('Não consegui abrir esse link: ' + err.message); return; }
  props.setProperty('CARGAS', link);
  ui.alert('Ligado à aba "' + aba.getName() + '" (' + Math.max(aba.getLastRow() - 1, 0) + ' linhas). No app, ligue Ajustes > Fretes FreteBras.');
}

function consulta(ss) {
  const link = PropertiesService.getScriptProperties().getProperty('CARGAS');
  return link ? SpreadsheetApp.openByUrl(link).getSheets()[0] : ss.getSheetByName('Consulta');
}

function doGet(e) {
  if (!e) throw new Error('doGet só funciona quando o app chama o link. Para conectar, escolha a função "conectar" e clique em Executar.');
  if (e.parameter.key !== chave()) return saida({ error: 'chave incorreta' });
  const ss = SpreadsheetApp.getActive();
  if (e.parameter.backup) {
    // O app pede o backup mais novo (a aba Backup guarda o mais novo na linha 2).
    const b = ss.getSheetByName('Backup');
    return saida({ Backup: b && b.getLastRow() > 1 ? b.getRange(2, 2).getValue() : '' });
  }
  const r = { versao: VERSAO };
  const abas = { Respostas: ss.getSheetByName('Respostas'), Consulta: consulta(ss) };
  Object.keys(abas).forEach(function (nome) {
    const aba = abas[nome];
    r[nome] = aba && aba.getLastRow() > 0 ? aba.getDataRange().getDisplayValues() : [];
  });
  return saida(r);
}

function doPost(e) {
  const b = JSON.parse(e.postData.contents);
  if (b.key !== chave()) return saida({ error: 'chave incorreta' });
  const trava = LockService.getScriptLock();
  trava.waitLock(20000);
  try {
    const ss = SpreadsheetApp.getActive();
    const aba = ss.getSheetByName(b.sheet) || ss.insertSheet(b.sheet);
    if (aba.getLastRow() === 0 && b.header && b.header.length) {
      aba.appendRow(b.header);
      aba.getRange(1, 1, 1, b.header.length).setFontWeight('bold').setBackground('#d3f5e3');
    }
    aba.setFrozenRows(1);
    const linhas = b.rows || [];
    if (linhas.length) {
      const largura = Math.max.apply(null, linhas.map(function (l) { return l.length; }));
      // Mais novas no topo: entram logo abaixo do cabeçalho, a última recebida primeiro.
      const cheias = linhas.map(function (l) { while (l.length < largura) l.push(''); return l; }).reverse();
      aba.insertRowsAfter(1, cheias.length);
      aba.getRange(2, 1, cheias.length, largura).setValues(cheias).setFontWeight('normal').setBackground(null);
    }
    return saida({ ok: true, linhas: linhas.length });
  } finally {
    trava.releaseLock();
  }
}

function criarAbas() {
  const ss = SpreadsheetApp.getActive();
  const abas = {
    Respostas: ['Palavras-chave', 'Resposta', 'Comparação', 'Quem', 'Ativo', 'Grupos'],
    Consulta: ['Código', 'Resultado'],
  };
  Object.keys(abas).forEach(function (nome) {
    const aba = ss.getSheetByName(nome) || ss.insertSheet(nome);
    if (aba.getLastRow() === 0) {
      aba.appendRow(abas[nome]);
      aba.getRange(1, 1, 1, abas[nome].length).setFontWeight('bold').setBackground('#d3f5e3');
      aba.setFrozenRows(1);
    }
  });
  const r = ss.getSheetByName('Respostas');
  // Planilhas criadas antes da coluna Grupos.
  if (r.getRange('F1').getValue() === '') r.getRange('F1').setValue('Grupos').setFontWeight('bold').setBackground('#d3f5e3');
  if (r.getLastRow() === 1) r.appendRow(['preço, valor, quanto custa', 'Nossos preços começam em R$ 50.', 'contém', 'todos', 'sim', 'não']);
  // Listas de escolha (só avisam se digitar outra coisa, para não travar textos antigos).
  const lista = function (col, itens) {
    r.getRange(col + '2:' + col + '1000').setDataValidation(
      SpreadsheetApp.newDataValidation().requireValueInList(itens, true).setAllowInvalid(true).build());
  };
  lista('C', ['contém', 'exata', 'começa com', 'termina com', 'parecida', 'curinga', 'regex', 'todas']);
  lista('D', ['todos', 'salvos', 'não salvos']);
  lista('E', ['sim', 'não']);
  lista('F', ['sim', 'não']);
}

function saida(o) {
  return ContentService.createTextOutput(JSON.stringify(o)).setMimeType(ContentService.MimeType.JSON);
}

const HTML = `<!DOCTYPE html><html><head><base target="_top">
<script src="https://cdnjs.cloudflare.com/ajax/libs/qrcodejs/1.0.0/qrcode.min.js"></script>
<style>
  body{font-family:Arial,sans-serif;margin:0;padding:8px 16px;color:#1b1b1b}
  h3{color:#128C5E;margin:8px 0}
  #qr{display:flex;justify-content:center;margin:16px 0}
  input{width:100%;padding:8px;box-sizing:border-box;margin:6px 0}
  .passo{background:#f1f7f4;border-radius:10px;padding:10px;margin:8px 0;font-size:13px}
  .erro{background:#fde7e7}
</style></head><body>
<div class="passo">Link do app da Web (confira com Implantar &gt; Gerenciar implantações):
  <input id="url" placeholder="https://script.google.com/macros/s/.../exec" onchange="gerar()">
</div>
<div id="msg" class="passo" style="display:none"></div>
<h3>Escaneie com o app</h3>
<div class="passo">No celular: ZapResponde &gt; Planilha &gt; <b>Ler QR code</b>.</div>
<div id="qr"></div>
<div class="passo">A chave fica guardada no script. Para trocar, use o menu ZapResponde &gt; Gerar nova chave.</div>
<div class="passo">Script versão <?!= VERSAO ?></div>
<script>
  const chave = <?!= JSON.stringify(qrKey) ?>;
  document.getElementById('url').value = <?!= JSON.stringify(qrUrl) ?>;
  function aviso(t, erro) {
    const m = document.getElementById('msg');
    m.style.display = t ? 'block' : 'none'; m.className = erro ? 'passo erro' : 'passo'; m.textContent = t;
  }
  function gerar() {
    const u = document.getElementById('url').value.trim();
    const box = document.getElementById('qr'); box.innerHTML = '';
    if (!u) { aviso('Falta implantar: Implantar > Nova implantação > App da Web, executar como Eu, acesso Qualquer pessoa. Depois cole o link acima.', true); return; }
    aviso('Testando o link...', false);
    google.script.run.withSuccessHandler(function (erro) {
      if (erro) { aviso(erro, true); return; }
      aviso('Link funcionando. Pode escanear.', false);
      new QRCode(box, { text: JSON.stringify({ zapresponde: 1, url: u, key: chave }), width: 240, height: 240 });
    }).withFailureHandler(function (e) { aviso('Não consegui testar: ' + e.message, true); }).testarLink(u);
  }
  gerar();
</script></body></html>`;
