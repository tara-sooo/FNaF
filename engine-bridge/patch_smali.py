#!/usr/bin/env python3
"""Patch disassembled user-owned FNaF1 Clickteam classes.dex without redistributing the game.

Usage:
    java -jar baksmali.jar disassemble classes.dex -o smali-base
    python3 engine-bridge/patch_smali.py smali-base
    java -jar smali.jar assemble smali-base -o classes-patched.dex

Replace classes.dex inside YOUR base.apk, then sign base+all split APKs with
one signing key. This repo deliberately does not include the original game.
"""
import argparse
from pathlib import Path
import re

PATTERN=re.compile(r'^\s*invoke-virtual\s+\{[^}]+\},\s*LRunLoop/CRun;->initRunLoop\(\)I\s*$',re.M)
RESULT=re.compile(r'^\s*move-result\s+v\d+\s*$')
INJECTION='    invoke-static {p0}, Lsovereign/bridge/MainRoomStart;->emit(LApplication/CRunApp;)V\n'

def patch(folder,source):
    folder=Path(folder)
    smali=folder/'Application'/'CRunApp.smali'
    output=folder/'sovereign'/'bridge'/'MainRoomStart.smali'
    original=smali.read_text('utf-8')
    if INJECTION.strip() in original:raise RuntimeError('Already patched')
    matches=list(PATTERN.finditer(original))
    if len(matches)!=1:raise RuntimeError('Expected exactly one initRunLoop()I call; found '+str(len(matches)))
    match=matches[0]
    method_start=original.rfind('.method ',0,match.start())
    method_end=original.find('.end method',match.end())
    if method_start<0 or method_end<0 or 'startTheFrame' not in original[method_start:match.start()]:
        raise RuntimeError('Cannot locate startTheFrame injection site')
    lines=original.splitlines(keepends=True)
    pos=0
    insert_after=None
    for i,line in enumerate(lines):
        pos+=len(line)
        if pos>=match.end():
            insert_after=i
            for j in range(i+1,len(lines)):
                if not lines[j].strip() or lines[j].lstrip().startswith('#'):continue
                if RESULT.fullmatch(lines[j]):insert_after=j
                break
            break
    if insert_after is None:raise RuntimeError('No insertion position')
    if output.exists():raise RuntimeError('Bridge helper already exists')
    helper=Path(source).read_bytes()
    if b'Lsovereign/bridge/MainRoomStart;' not in helper:raise RuntimeError('Bad helper smali')
    lines.insert(insert_after+1,INJECTION)
    output.parent.mkdir(parents=True,exist_ok=True)
    smali.write_text(''.join(lines),'utf-8')
    output.write_bytes(helper)
    print('Patched CRunApp.startTheFrame after initRunLoop; will emit game frame#5 epoch to controller')

if __name__=='__main__':
    p=argparse.ArgumentParser()
    p.add_argument('baksmali_directory',type=Path)
    p.add_argument('--helper',type=Path,default=Path(__file__).resolve().parent/'smali'/'sovereign'/'bridge'/'MainRoomStart.smali')
    args=p.parse_args()
    patch(args.baksmali_directory,args.helper)
