import json,sys
o=json.load(open(sys.argv[1])); n=json.load(open(sys.argv[2]))
M=['get','post','put','patch','delete']
def ops(j):
  r={}
  for p,v in j['paths'].items():
    for m in M:
      if m in v: r[(m.upper(),p)]=v[m]
  return r
oo,no=ops(o),ops(n)
print('== removed ops'); [print(' ',k, oo[k].get('operationId')) for k in sorted(set(oo)-set(no))]
print('== added ops'); [print(' ',k, no[k].get('operationId')) for k in sorted(set(no)-set(oo))]
print('== changed ops')
for k in sorted(set(oo)&set(no)):
  a,b=oo[k],no[k]
  if json.dumps(a,sort_keys=True)!=json.dumps(b,sort_keys=True):
    diffs=[f for f in set(a)|set(b) if json.dumps(a.get(f),sort_keys=True)!=json.dumps(b.get(f),sort_keys=True)]
    print(' ',k,diffs)
    if 'parameters' in diffs:
      pa={(x.get('in'),x.get('name')) for x in a.get('parameters',[])}; pb={(x.get('in'),x.get('name')) for x in b.get('parameters',[])}
      print('    params -',pa-pb,'+',pb-pa)
so=o['components']['schemas']; sn=n['components']['schemas']
print('== removed schemas', sorted(set(so)-set(sn)))
print('== added schemas', sorted(set(sn)-set(so)))
print('== changed schemas')
def props(s):
  out={}
  def walk(x,pre=''):
    if isinstance(x,dict):
      if 'properties' in x:
        for k,v in x['properties'].items(): out[pre+k]=json.dumps(v,sort_keys=True)
      if 'required' in x: out[pre+'@required']=json.dumps(sorted(x['required']))
      for key in ('anyOf','oneOf','allOf'):
        if key in x:
          for i,y in enumerate(x[key]): walk(y,pre+f'{key}[{i}].')
      if 'enum' in x: out[pre+'@enum']=json.dumps(x['enum'])
      if 'const' in x: out[pre+'@const']=json.dumps(x['const'])
      if '$ref' in x: out[pre+'@ref']=x['$ref']
      if 'type' in x and 'properties' not in x: out[pre+'@type']=json.dumps(x['type'])
  walk(s); return out
for k in sorted(set(so)&set(sn)):
  if json.dumps(so[k],sort_keys=True)!=json.dumps(sn[k],sort_keys=True):
    a,b=props(so[k]),props(sn[k])
    print(' ',k)
    for f in sorted(set(a)|set(b)):
      if a.get(f)!=b.get(f):
        print('     ',f,'\n        old:',(a.get(f) or '-')[:300],'\n        new:',(b.get(f) or '-')[:300])
