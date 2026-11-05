"""Write the kit port plan: every Flutter kit file at a ref, its native file(s) and goldens, in batches.

Usage: port_plan.py OUT.md OC_APP_REPO NATIVE_UI_DIR [REF]
"""
import subprocess, re, os, collections, sys
repo=sys.argv[2]; native=sys.argv[3]; ref=sys.argv[4] if len(sys.argv)>4 else 'mobile-next/master'
ls=lambda p: subprocess.run(['git','-C',repo,'ls-tree','-r','--name-only',ref,p],capture_output=True,text=True).stdout.split()
dart=[p for p in ls('lib/ui/kit') if p.endswith('.dart')]
goldens=[os.path.basename(p)[:-4] for p in ls('test/goldens/kit') if p.endswith('.png') and '/failures/' not in p]
# native index: file -> text
nat={}
for root,_,files in os.walk(native):
    if '/build/' in root or 'Test' in root or '/test' in root.lower(): continue
    for f in files:
        if f.endswith('.kt') and '/commonMain/' in os.path.join(root,f):
            nat[os.path.join(root,f)]=open(os.path.join(root,f),errors='ignore').read()
def native_for(path):
    base=os.path.basename(path)
    pascal=''.join(x.title() for x in base[:-5].split('_'))
    hits=[k for k,v in nat.items() if base in v or os.path.basename(k)==pascal+'.kt' or f"Dart `{pascal}`" in v]
    return sorted({h.split('/oc/ui/')[-1] for h in hits})
def stem(path): return os.path.basename(path)[:-5]
def gstem(path):
    s=stem(path)
    return s if s.startswith('kit_') else 'kit_'+s
_stems=sorted({gstem(p) for p in dart}, key=len, reverse=True)
_owner={}
for g in goldens:
    for st in _stems:
        if g==st or g.startswith(st+'_'):
            _owner[g]=st; break
def golds(path):
    st=gstem(path)
    return [g for g in goldens if _owner.get(g)==st]
# dependency layers by path / name
def layer(p):
    n=p.split('lib/ui/kit/')[1]
    if n.startswith('live_background/'): return 9,'live background'
    if n.startswith('motion/') or stem(p) in ('kit_tokens','kit_text','kit_shape','kit_icon','kit_tappable','kit_surface','kit_effects','kit_layout','kit_motion','kit_bidi','kit_time','kit_since','kit_copy','kit_redact','kit_bottom_inset','kit_text_scale'): return 0,'foundation'
    if n.startswith('glass/'): return 10,'not ported (owner 2026-10-26: no glass)'
    if n.startswith('style/'): return 0,'foundation'
    if stem(p) in ('kit_buttons','kit_icon_button','kit_chip','kit_filter_chip','kit_filter_chips','kit_field','kit_search_field','kit_segmented','kit_choice_list','kit_divider','kit_section_label','kit_swatch','kit_status_mark','kit_status_tag','kit_task_mark','kit_needs_you','kit_progress','kit_progress_row','kit_level_meter','kit_skeleton_transcript','kit_term','kit_key_value','kit_technical_value','kit_qr','kit_image','kit_illustration'): return 1,'controls'
    if stem(p).startswith('kit_row') or stem(p) in ('kit_sliver_row_group','kit_group_note','kit_details_fold','kit_feed_item','kit_swipe_action','kit_mini_table','kit_receipt','kit_notice','kit_status_line','kit_status_slot','kit_state_view','kit_last_known','kit_checklist','kit_consequences','kit_code_block','kit_code_block_parts','kit_log_panel','kit_action_stack','kit_panel','kit_task_card'): return 2,'rows, groups and states'
    if stem(p) in ('kit_sheet','kit_sheet_frame','kit_sheet_modal','kit_sheet_parts','kit_confirm_sheet','kit_dialog','kit_menu','kit_undo','kit_date_time_picker','kit_request_card','kit_request_answers','kit_request_sheet','kit_context_sheet','kit_capability_explainer','kit_floating_action','kit_jump_pill','kit_scrollbar'): return 3,'sheets, dialogs, menus and requests'
    if stem(p) in ('kit_top_bar','kit_screen','kit_nav','kit_breadcrumb','kit_page_route','kit_arrival','kit_status_line') : return 4,'frame and navigation'
    if n.startswith('chat/') or stem(p).startswith('kit_composer') or stem(p).startswith('kit_context') or stem(p) in ('kit_diff_view','kit_ask_line','kit_sense_ask'): return 5,'chat'
    if stem(p).startswith('kit_viewer') or stem(p) in ('kit_image_gallery','kit_image_markup','kit_terminal_view','terminal_key_bar','kit_scanner','kit_chart','kit_work_graph','kit_board_lane'): return 6,'content and charts'
    if n.startswith('team/') or 'agent' in stem(p): return 7,'agents and team'
    if n.startswith('scenes/'): return 8,'scenes'
    return 7,'other parts'
rows=[]
for p in sorted(dart):
    L,name=layer(p)
    g=golds(p)
    rows.append((L,name,p.split('lib/ui/kit/')[1],native_for(p),g))
rows.sort(key=lambda r:(r[0],-len(r[4]),r[2]))
out=[]
w=out.append
w('# Port plan: the Flutter kit, part by part (2026-11-05)\n')
w('Owner redirect, 2026-11-05: rebuild the Flutter design system and kit exactly; improvements come later, one at a time, with the owner\'s approval. This plan lists every Flutter kit file at `mobile-next/master` (8302de537, the 2.1.1 product), its native file(s) in `native/ui/*`, and its goldens under `test/goldens/kit/`, grouped into batches of about ten parts for one agent each, in dependency order (a batch only needs batches above it).\n')
w('## How each part is done\n')
w('1. Read the Dart part and its golden test (`test/goldens/kit/<part>_golden_test.dart`): the scene inputs, the size, the frame.\n2. Fetch its goldens: `native/tool/design/fetch_flutter_goldens.py --repo <oc_app> --out looks-gate/flutter <stem>...`.\n3. Add a gate case in `native/ui/kit-chat/src/desktopTest/.../gate/` with the same inputs (`FlutterGoldenGate.check("<stem>") { ... }`; `Frame.Part` repeats `kitGalleryPart`, `Frame.Screen` for scenes with their own backdrop).\n4. Change the native part until its diff % is as low as the fonts allow; keep every existing native signature, add parameters with defaults only. Tokens come from `ui/design/generated/FlutterKit.kt` (run `native/tool/design/gen_tokens.py`), never typed.\n5. Commit the part with its gate case and report the diff % per golden (light and dark).\n')
w('Golden counts below are per part (light and dark, all sizes). "–" means no native file names the Dart part yet (missing, or ported under another name: check the kit map).\n')
batch=0; cur=[]; prev=None
def flush():
    global batch,cur
    if not cur: return
    batch+=1
    w(f'\n### Batch {batch}: {cur[0][1]} ({len(cur)} parts, {sum(len(r[4]) for r in cur)} goldens)\n')
    w('| Flutter part | Native file(s) | Goldens |')
    w('|---|---|---|')
    for r in cur:
        nat=', '.join(f'`{x}`' for x in r[3][:3]) + (' …' if len(r[3])>3 else '') if r[3] else '–'
        g=len(r[4])
        ex=', '.join(sorted({re.sub(r'_(light|dark)$','',x) for x in r[4]})[:4])
        w(f'| `lib/ui/kit/{r[2]}` | {nat} | {g}{" ("+ex+(" …" if len({re.sub(r"_(light|dark)$","",x) for x in r[4]})>4 else "")+")" if g else ""} |')
    cur=[]
for r in rows:
    if prev is not None and (r[0]!=prev or len(cur)>=10): flush()
    cur.append(r); prev=r[0]
flush()
w(f"\n---\n{len(rows)} Flutter kit files, {batch} batches, {len(goldens)} kit goldens ({sum(1 for g in goldens if g in _owner)} assigned to a part; the rest belong to galleries named after scenes).")
w("\nThe gate lives in `native/ui/kit-chat/src/desktopTest/.../gate/FlutterGoldenGate.kt` because kit-chat sees both kits; the first batch on `:ui:kit-content` parts moves it to a shared test fixture (one small commit).")
open(sys.argv[1],'w').write('\n'.join(out)+'\n')
print(len(rows),batch)
