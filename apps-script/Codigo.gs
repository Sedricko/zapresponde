// ZapResponde: cole este código em Extensões > Apps Script da sua planilha.
// Troque a CHAVE abaixo por uma senha sua e use a mesma no app.
// Depois: Implantar > Nova implantação > App da Web, executar como "Eu", acesso "Qualquer pessoa".
const CHAVE = 'TROQUE-ESTA-CHAVE';

function doGet(e) {
  if (e.parameter.key !== CHAVE) return saida({ error: 'chave incorreta' });
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
  if (b.key !== CHAVE) return saida({ error: 'chave incorreta' });
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

// Rode uma vez pelo editor para criar as abas com os cabeçalhos.
function criarAbas() {
  const ss = SpreadsheetApp.getActive();
  const abas = {
    Respostas: ['Palavras-chave', 'Resposta', 'Comparação', 'Quem', 'Ativo'],
    Consulta: ['Código', 'Resultado'],
  };
  Object.keys(abas).forEach(function (nome) {
    const aba = ss.getSheetByName(nome) || ss.insertSheet(nome);
    if (aba.getLastRow() === 0) aba.appendRow(abas[nome]);
  });
  const r = ss.getSheetByName('Respostas');
  if (r.getLastRow() === 1) r.appendRow(['preço, valor, quanto custa', 'Nossos preços começam em R$ 50.', 'contém', 'todos', 'sim']);
}

function saida(o) {
  return ContentService.createTextOutput(JSON.stringify(o)).setMimeType(ContentService.MimeType.JSON);
}
