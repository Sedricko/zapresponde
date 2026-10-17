// ZapResponde: cole este código em Extensões > Apps Script da sua planilha e salve.
// Depois: Implantar > Nova implantação > App da Web (executar como "Eu", acesso "Qualquer pessoa").
// Por fim, recarregue a planilha e use o menu ZapResponde > Conectar celular. Não precisa editar nada aqui.

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
    .addItem('Gerar nova chave', 'novaChave')
    .addToUi();
}

// Cria as abas que faltam e mostra o QR code que o app lê.
function conectar() {
  criarAbas();
  const url = ScriptApp.getService().getUrl() || '';
  const t = HtmlService.createTemplate(HTML);
  t.qrUrl = url.endsWith('/exec') ? url : '';
  t.qrKey = chave();
  SpreadsheetApp.getUi().showModalDialog(t.evaluate().setWidth(380).setHeight(560), 'Conectar o ZapResponde');
}

function novaChave() {
  PropertiesService.getScriptProperties().deleteProperty('CHAVE');
  chave();
  conectar();
}

function doGet(e) {
  if (e.parameter.key !== chave()) return saida({ error: 'chave incorreta' });
  const ss = SpreadsheetApp.getActive();
  const r = {};
  ['Respostas', 'Consulta'].forEach(function (nome) {
    const aba = ss.getSheetByName(nome);
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
    if (aba.getLastRow() === 0 && b.header && b.header.length) aba.appendRow(b.header);
    const linhas = b.rows || [];
    if (linhas.length) {
      const largura = Math.max.apply(null, linhas.map(function (l) { return l.length; }));
      const cheias = linhas.map(function (l) { while (l.length < largura) l.push(''); return l; });
      aba.getRange(aba.getLastRow() + 1, 1, cheias.length, largura).setValues(cheias);
    }
    return saida({ ok: true, linhas: linhas.length });
  } finally {
    trava.releaseLock();
  }
}

function criarAbas() {
  const ss = SpreadsheetApp.getActive();
  const abas = {
    Respostas: ['Palavras-chave', 'Resposta', 'Comparação', 'Quem', 'Ativo'],
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
  if (r.getLastRow() === 1) r.appendRow(['preço, valor, quanto custa', 'Nossos preços começam em R$ 50.', 'contém', 'todos', 'sim']);
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
<div id="semUrl" class="passo erro" style="display:none">
  <b>Falta implantar.</b> No Apps Script, clique em <b>Implantar &gt; Nova implantação</b>, escolha <b>App da Web</b>,
  executar como <b>Eu</b>, acesso <b>Qualquer pessoa</b>. Depois cole aqui o link que termina em /exec:
  <input id="url" placeholder="https://script.google.com/macros/s/.../exec" oninput="gerar()">
</div>
<h3>Escaneie com o app</h3>
<div class="passo">No celular: ZapResponde &gt; Planilha &gt; <b>Ler QR code</b>.</div>
<div id="qr"></div>
<div class="passo">A chave fica guardada no script. Para trocar, use o menu ZapResponde &gt; Gerar nova chave.</div>
<script>
  const chave = <?!= JSON.stringify(qrKey) ?>;
  const url = <?!= JSON.stringify(qrUrl) ?>;
  if (!url) document.getElementById('semUrl').style.display = 'block';
  function gerar() {
    const u = url || document.getElementById('url').value.trim();
    const box = document.getElementById('qr'); box.innerHTML = '';
    if (!u.endsWith('/exec')) return;
    new QRCode(box, { text: JSON.stringify({ zapresponde: 1, url: u, key: chave }), width: 260, height: 260 });
  }
  gerar();
</script></body></html>`;
