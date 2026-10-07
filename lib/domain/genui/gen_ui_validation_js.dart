/// Shared helper-side validation. Errors contain stable codes, never payloads.
const String genUiValidationJavascript = r'''
function normalizeGenUiCard(input) {
  const fail = (code = 'badValue') => { throw new Error(code); };
  let count = 0;
  let stringBytes = 0;
  const boundString = s => { if (s.length > 65536 || scalar(s) > 32768) fail('tooLarge'); stringBytes += Buffer.byteLength(s, 'utf8'); if(stringBytes > 32768) fail('tooLarge'); };
  const seen = new Set();
  const scalar = s => {
    for (let i = 0; i < s.length; i++) {
      const c = s.charCodeAt(i);
      if (c >= 0xd800 && c <= 0xdbff) {
        if (++i >= s.length || s.charCodeAt(i) < 0xdc00 || s.charCodeAt(i) > 0xdfff) fail();
      } else if (c >= 0xdc00 && c <= 0xdfff) fail();
    }
    return [...s].length;
  };
  const bound = (x, depth = 1) => {
    if (++count > 4096 || depth > 8) fail('tooLarge');
    if (x === null || typeof x === 'boolean') return;
    if (typeof x === 'string') { boundString(x); return; }
    if (typeof x === 'number') { if (!Number.isFinite(x)) fail(); return; }
    if (typeof x !== 'object' || seen.has(x)) fail();
    if (!Array.isArray(x) && Object.getPrototypeOf(x) !== Object.prototype && Object.getPrototypeOf(x) !== null) fail();
    seen.add(x);
    for (const [k,v] of Object.entries(x)) { if (!Array.isArray(x)) boundString(k); bound(v, depth + 1); }
    seen.delete(x);
  };
  bound(input);
  if (Buffer.byteLength(JSON.stringify(input), 'utf8') > 32768) fail('tooLarge');
  const obj = (x, allowed, required = []) => {
    if (!x || typeof x !== 'object' || Array.isArray(x)) fail();
    if (Object.keys(x).some(k => !allowed.includes(k))) fail('unknownKey');
    if (required.some(k => !Object.hasOwn(x,k)) || Object.values(x).some(v => v === null)) fail();
    return x;
  };
  const text = (x, max = 2000, label = false, min = 0) => {
    if (typeof x !== 'string' || scalar(x) > max) fail();
    let s = x.replace(/[\x00-\x08\x0b-\x1f\x7f-\x9f\u061c\u200e-\u200f\u202a-\u202e\u2066-\u2069]/g, '');
    if (label) s = s.trim();
    if (scalar(s) < min) fail();
    return s;
  };
  const arr = (x, min, max) => { if (!Array.isArray(x) || x.length < min || x.length > max) fail(); return x; };
  const num = x => { if (typeof x !== 'number' || !Number.isFinite(x)) fail(); return x; };
  const int = (x,min,max) => { num(x); if (!Number.isInteger(x) || x < min || x > max) fail(); return x; };
  const bool = x => { if (typeof x !== 'boolean') fail(); return x; };
  const pick = (x, vals) => { if (!vals.includes(x)) fail(); return x; };
  const id = (x, re) => { if (typeof x !== 'string' || re.exec(x)?.[0] !== x) fail(); return x; };
  const secret = s => { if (/(password|passwd|token|secret|apikey)/i.test(s.replace(/[^a-z0-9]/gi,''))) fail('secretField'); };
  const unique = xs => { if (new Set(xs).size !== xs.length) fail(); };
  const optText = (o,k,max,label=false,min=0) => Object.hasOwn(o,k) ? {[k]:text(o[k],max,label,min)} : {};
  const option = o => {
    obj(o,['id','label','detail'],['id','label']);
    const r = {id:id(o.id,/^[a-zA-Z0-9_-]{1,32}$/),label:text(o.label,120,true,1),...optText(o,'detail',200)};
    secret(r.id); secret(r.label); return r;
  };
  const options = (v,min,max) => { const r = arr(v,min,max).map(option); unique(r.map(o=>o.id)); return r; };
  const fieldValue = (v,f) => {
    switch(f.type) {
      case 'text': case 'multiline': if (typeof v !== 'string' || scalar(v)>2000 || (f.required && !v.trim())) fail(); return v;
      case 'number': num(v); if ((f.min !== undefined && v<f.min)||(f.max !== undefined && v>f.max)) fail(); return v;
      case 'toggle': return bool(v);
      case 'select': if (typeof v !== 'string' || !f.options.some(o=>o.id===v)) fail(); return v;
      case 'date': {
        if (typeof v !== 'string' || v.length !== 10 || !/^\d{4}-\d{2}-\d{2}$/.test(v)) fail();
        const [y,m,d]=v.split('-').map(Number); const leap=y%4===0&&(y%100!==0||y%400===0);
        if (y<1 || m<1 || m>12 || d<1 || d>[31,leap?29:28,31,30,31,30,31,31,30,31,30,31][m-1]) fail(); return v;
      }
    }
  };
  const field = o => {
    obj(o,['id','label','type','required','placeholder','default','options','min','max'],['id','label','type']);
    const r={id:id(o.id,/^[a-zA-Z0-9_]{1,32}$/),label:text(o.label,120,true,1),type:pick(o.type,['text','multiline','number','toggle','select','date']),required:Object.hasOwn(o,'required')?bool(o.required):false,...optText(o,'placeholder',120)};
    secret(r.id); secret(r.label);
    if (r.type==='select') r.options=options(o.options,2,20); else if (Object.hasOwn(o,'options')) fail();
    for (const k of ['min','max']) if (Object.hasOwn(o,k)) { if(r.type!=='number') fail(); r[k]=num(o[k]); }
    if(r.min!==undefined&&r.max!==undefined&&r.min>r.max) fail();
    if(Object.hasOwn(o,'default')) r.default=fieldValue(o.default,r);
    return r;
  };
  const ask = o => {
    obj(o,['kind','options','multi','fields','submitLabel','confirmLabel','cancelLabel','tone','purpose','max'],['kind']);
    switch(o.kind) {
      case 'choice': obj(o,['kind','options','multi'],['kind','options']); return {kind:o.kind,options:options(o.options,2,8),multi:Object.hasOwn(o,'multi')?bool(o.multi):false};
      case 'form': { obj(o,['kind','fields','submitLabel'],['kind','fields']); const fields=arr(o.fields,1,12).map(field); unique(fields.map(f=>f.id)); return {kind:o.kind,fields,...optText(o,'submitLabel',24,true,1)}; }
      case 'confirm': obj(o,['kind','confirmLabel','cancelLabel','tone'],['kind']); return {kind:o.kind,...optText(o,'confirmLabel',24,true,1),...optText(o,'cancelLabel',24,true,1),tone:Object.hasOwn(o,'tone')?pick(o.tone,['normal','danger']):'normal'};
      case 'photo': obj(o,['kind','purpose','max'],['kind','purpose']); return {kind:o.kind,purpose:text(o.purpose,200,true,1),max:Object.hasOwn(o,'max')?int(o.max,1,4):1};
      case 'file': case 'voice': fail('unsupportedAsk'); break;
      default: fail();
    }
  };
  const node = o => {
    if (!o || typeof o.type !== 'string') fail();
    switch(o.type) {
      case 'text': obj(o,['type','text'],['type','text']); return {type:o.type,text:text(o.text)};
      case 'keyValue': obj(o,['type','rows'],['type','rows']); return {type:o.type,rows:arr(o.rows,0,20).map(r=>{obj(r,['key','value'],['key','value']);return {key:text(r.key,60,true,1),value:text(r.value,200)};})};
      case 'list': obj(o,['type','items','style'],['type','items','style']); return {type:o.type,items:arr(o.items,0,30).map(r=>{obj(r,['text','done'],['text']);return {text:text(r.text),done:Object.hasOwn(r,'done')?bool(r.done):false};}),style:pick(o.style,['bullet','check'])};
      case 'table': {obj(o,['type','columns','rows'],['type','columns','rows']);const columns=arr(o.columns,1,6).map(x=>text(x,80,true));return {type:o.type,columns,rows:arr(o.rows,0,20).map(r=>arr(r,columns.length,columns.length).map(x=>text(x,80)))};}
      case 'chart': {obj(o,['type','kind','unit','labels','series'],['type','kind','labels','series']); const labels=arr(o.labels,1,30).map(x=>text(x,80,true));return {type:o.type,kind:pick(o.kind,['bar','line']),...optText(o,'unit',24,true),labels,series:arr(o.series,1,3).map(r=>{obj(r,['name','values'],['name','values']);return {name:text(r.name,80,true),values:arr(r.values,labels.length,labels.length).map(num)};})};}
      case 'code': obj(o,['type','language','text'],['type','text']);return {type:o.type,...optText(o,'language',32,true),text:text(o.text,4000)};
      case 'diffStat': obj(o,['type','files'],['type','files']);return {type:o.type,files:arr(o.files,0,30).map(r=>{obj(r,['path','added','removed'],['path','added','removed']);return {path:text(r.path,256),added:int(r.added,0,9007199254740991),removed:int(r.removed,0,9007199254740991)};})};
      case 'progress': obj(o,['type','label','value'],['type','label','value']);num(o.value);if(o.value<0||o.value>1)fail();return {type:o.type,label:text(o.label,120,true),value:o.value};
      case 'callout': obj(o,['type','tone','text'],['type','tone','text']);return {type:o.type,tone:pick(o.tone,['info','warning','success']),text:text(o.text,500)};
      case 'link': {obj(o,['type','label','url'],['type','label','url']);if(typeof o.url!=='string'||scalar(o.url)>2048||/[\s\x00-\x20\x7f-\x9f\u061c\u200e-\u200f\u202a-\u202e\u2066-\u2069\\]/u.test(o.url)||!/^https:\/\//i.test(o.url))fail(); let u;try {u=new URL(o.url);} catch {fail();} if(u.protocol!=='https:'||!u.hostname||u.username||u.password||o.url.split('/')[2].includes('@'))fail();return {type:o.type,label:text(o.label,120,true,1),url:o.url};}
      default: fail();
    }
  };
  obj(input,['v','id','title','body','ask'],['v','id','title','body']);
  if(input.v!==1) fail('version');
  return {v:1,id:id(input.id,/^[a-z0-9-]{1,48}$/),title:text(input.title,120,true,1),body:arr(input.body,0,40).map(node),...(Object.hasOwn(input,'ask')?{ask:ask(input.ask)}:{})};
}
''';
