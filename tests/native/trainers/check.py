#!/usr/bin/env python3
"""Host parser/actual-x86 wrapper test. pip install unicorn for execution.
Optional argument: directory containing individually downloaded ETM samples.
No trainer payload is redistributed in this source tree.
"""
import ctypes as c, pathlib, struct, subprocess, sys, random
ROOT=pathlib.Path(__file__).resolve().parents[3]
SO=ROOT/'build/advanced-tests/etm.so'; SO.parent.mkdir(parents=True,exist_ok=True)
subprocess.run(['cc','-Wall','-Wextra','-Werror','-shared','-fPIC','-I'+str(ROOT),str(ROOT/'native/core/etm.c'),'-o',str(SO)],check=True)
lib=c.CDLL(str(SO))
class Info(c.Structure):
 _fields_=[('options',c.c_uint32),('capacity',c.c_uint32),('entry',c.c_uint32),('noptions',c.c_uint32),('nids',c.c_uint32),('ids',c.c_uint32*3)]
lib.nc_etm_parse.argtypes=[c.c_void_p,c.c_size_t,c.POINTER(Info)];lib.nc_etm_parse.restype=c.c_char_p
lib.nc_etm_matches.argtypes=[c.POINTER(Info),c.c_uint32];lib.nc_etm_matches.restype=c.c_bool
lib.nc_etm_stub.argtypes=[c.c_void_p,c.c_uint32,c.c_uint32,c.c_uint32]
def parse(d):
 i=Info(); e=lib.nc_etm_parse(c.create_string_buffer(bytes(d)),len(d),c.byref(i));return i,e
# Fixture with no-op trainer, two IDs, name/note/one option, then sentinel.
d=bytearray(96)
for p,v in [(0,96),(6,35),(10,44),(14,45),(18,36),(22,35),(36,0x12345678),(45,61),(49,66),(53,71)]:struct.pack_into('<I',d,p,v)
d[4]=1;d[35]=0xc3;d[61:66]=b'Name\0';d[66:71]=b'Note\0';d[71:78]=b'Option\0'
i,e=parse(d);assert e is None,(e,d);assert i.noptions==1 and i.nids==2
assert lib.nc_etm_matches(c.byref(i),0x12345678) and not lib.nc_etm_matches(c.byref(i),0)
assert not lib.nc_etm_matches(c.byref(i),0xdeadbeef)
for n in range(len(d)):assert parse(d[:n])[1] is not None
for p in [0,6,10,14,18,22,26,45,49,53]:
 bad=d.copy();struct.pack_into('<I',bad,p,0xffffffff);assert parse(bad)[1] is not None,p
bad=d.copy();bad[71:]=b'A'*(len(d)-71);assert parse(bad)[1] is not None
rng=random.Random(20261031)
for _ in range(10000):
 bad=d.copy()
 for j in range(rng.randrange(1,9)):bad[rng.randrange(len(bad))]=rng.randrange(256)
 parse(bad)
print('Parser: valid fixture, title gating, all truncations, bad offsets/strings, 10000 mutations PASS')
# Execute the exact C-generated wrapper in a second x86 emulator, check all
# GPRs, ESP and EFLAGS after trainer return (including caller's direction flag).
from unicorn import Uc,UC_ARCH_X86,UC_MODE_32
from unicorn.x86_const import *
REGS=[UC_X86_REG_EAX,UC_X86_REG_ECX,UC_X86_REG_EDX,UC_X86_REG_EBX,UC_X86_REG_ESP,UC_X86_REG_EBP,UC_X86_REG_ESI,UC_X86_REG_EDI]
def run(data, enabled=False, real=False):
 i,e=parse(data);assert not e,e
 base=0x83e00000;stop=0x11000;stack=0x1f0000
 m=Uc(UC_ARCH_X86,UC_MODE_32);m.mem_map(0x10000,0x200000);m.mem_map(base,0x20000)
 b=bytearray(data);b[i.options:i.options+i.capacity]=bytes(i.capacity);b[i.options]=int(enabled)
 m.mem_write(base,bytes(b));stub=c.create_string_buffer(28);lib.nc_etm_stub(stub,base,base+i.entry,stop);m.mem_write(base+len(b),stub.raw)
 vals=[0x112233,0x223344,0x334455,0x445566,stack,0x556677,0x667788,0x778899]
 for r,v in zip(REGS,vals):m.reg_write(r,v)
 m.reg_write(UC_X86_REG_EFLAGS,0x602)
 if real:
  m.mem_write(0x10114,struct.pack('<I',0x3c73084b));m.mem_write(0xdf36a,b'\xcc'*8)
 m.emu_start(base+len(b),stop,count=100000)
 assert m.reg_read(UC_X86_REG_EIP)==stop
 assert [m.reg_read(r) for r in REGS]==vals
 assert m.reg_read(UC_X86_REG_EFLAGS)==0x602
 if real:assert m.mem_read(0xdf36a,8)==(b'\x90'*8 if enabled else b'\xcc'*8)
run(d)
print('x86 wrapper: GPR/ESP/EFLAGS preservation and XBE continuation PASS')
if len(sys.argv)>1:
 files=[p for p in pathlib.Path(sys.argv[1]).iterdir() if p.suffix.lower()=='.etm']
 for p in files:
  i,e=parse(p.read_bytes());assert not e,(p.name,e)
 print(f'Archive ETM headers: {len(files)} samples PASS')
 sample=pathlib.Path(sys.argv[1])/'007 Agent Under Fire p1.ETM'
 run(sample.read_bytes(),False,True);run(sample.read_bytes(),True,True)
 print('Real Agent Under Fire +1: disabled leaves code alone; enabled writes eight NOPs; returns preserving CPU state PASS')
