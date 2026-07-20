import numpy as np, wave, os
SR=44100; OUT=os.path.join(os.path.dirname(os.path.abspath(__file__)),"..","work","bgm_30s.wav")
def env(n,a,d,sl,r):
    e=np.zeros(n); ai=min(max(1,int(a*SR)),n); e[:ai]=np.linspace(0,1,ai)
    di=min(max(1,int(d*SR)),n-ai)
    if di>0: e[ai:ai+di]=np.linspace(1,sl,di)
    if n-ai-di>0: e[ai+di:]=sl
    ri=min(max(1,int(r*SR)),n)
    e[-ri:]*=np.linspace(1,0,ri); return e
def noise(d): return np.random.rand(int(d*SR))*2-1
def kick(d=0.35):
    n=int(d*SR); t=np.arange(n)/SR; f=120*np.exp(-t*35)+45
    body=np.sin(2*np.pi*np.cumsum(f)/SR)*np.exp(-t*7)
    c=noise(0.006)*np.exp(-np.arange(int(0.006*SR))/SR*400); body[:len(c)]+=c*0.6
    return np.tanh(body*1.4)*0.95
def sub(f,d):
    n=int(d*SR); t=np.arange(n)/SR
    o=(np.sin(2*np.pi*f*t)+0.25*np.sin(2*np.pi*2*f*t))*env(n,0.008,d*0.9,0.2,0.06)
    return np.tanh(o*1.3)*0.8
def snare(d=0.22):
    n=int(d*SR); t=np.arange(n)/SR
    tone=(np.sin(2*np.pi*180*t)+np.sin(2*np.pi*330*t))*0.4*np.exp(-t*22)
    nz=np.diff(noise(d),prepend=0)*np.exp(-t*16); return np.tanh(tone+nz*1.2)*0.85
def clap(d=0.25):
    n=int(d*SR); t=np.arange(n)/SR; nz=np.diff(noise(d),prepend=0); e=np.exp(-t*18)
    for off in (0.01,0.02,0.03):
        i=int(off*SR); e[i:]+=np.exp(-np.arange(n-i)/SR*18)*0.7
    return np.tanh(nz*e*1.1)*0.8
def hat(open=False):
    d=0.14 if open else 0.05; n=int(d*SR); t=np.arange(n)/SR
    nz=np.diff(np.diff(noise(d),prepend=0),prepend=0); return nz*np.exp(-t*(30 if open else 90))*0.5
def pluck(f,d):
    n=int(d*SR); t=np.arange(n)/SR; saw=2*(t*f-np.floor(0.5+t*f))
    return saw*np.exp(-t*9)*env(n,0.003,d,0.0,0.04)*0.35
def punch(d=0.4):
    n=int(d*SR); t=np.arange(n)/SR; f=180*np.exp(-t*40)+70
    thud=np.sin(2*np.pi*np.cumsum(f)/SR)*np.exp(-t*11)
    s=np.diff(noise(0.02),prepend=0)*np.exp(-np.arange(int(0.02*SR))/SR*300); thud[:len(s)]+=s*0.8
    return np.tanh(thud*1.6)*0.98
def place(buf,sig,t,g=1.0):
    i=int(t*SR); j=min(len(buf),i+len(sig))
    if i<len(buf): buf[i:j]+=sig[:j-i]*g
np.random.seed(7)
BPM=140; beat=60/BPM; step=beat/4; bar=beat*4; TOTAL=30.0
a=np.zeros(int(TOTAL*SR))
# 섹션별 강도(엔벨로프): 훅(0-3) 미니멀 → 쉐도우(3-11) 풀드랍 → 루틴/성장(11-20) 유지 → 몽타주(20-24) 가장 강 → 로고/CTA(24-30) 정리
def intensity(tt):
    if tt<3: return 0.5
    if tt<20: return 1.0
    if tt<24: return 1.15
    return 0.9
nb=int(TOTAL/bar)+1
roots=[55,55,43.65,49.0]
for b in range(nb):
    t0=b*bar
    if t0>=TOTAL: break
    g=intensity(t0)
    for s in [0,6,10]: place(a,kick(),t0+s*step,0.95*g)
    place(a,snare(),t0+8*step,0.9*g); place(a,clap(),t0+8*step,0.5*g)
    place(a,sub(roots[b%4],bar*0.98),t0,0.9*g)
    for s in range(16):
        place(a,hat(open=(s==14)),t0+s*step,(0.9 if s%2==0 else 0.5)*0.5*g)
    if b%2==0:
        for s,f in [(0,220),(3,261.6),(6,329.6),(10,246.9)]: place(a,pluck(f,beat*0.9),t0+s*step,0.6*g)
# 임팩트 악센트: 시작·전환(3s 직전)·CTA(27s)
place(a,punch(),0.0,1.0); place(a,punch(),2.85,1.0); place(a,punch(0.6),27.0,1.0); place(a,sub(55,1.4),27.0,0.6)
a=a/(np.max(np.abs(a))+1e-9)*0.97
# 30.0초 정확히 자르고 인/아웃 페이드
a=a[:int(TOTAL*SR)]; fi=int(0.05*SR); fo=int(0.8*SR)
a[:fi]*=np.linspace(0,1,fi); a[-fo:]*=np.linspace(1,0,fo)
pcm=(np.clip(a,-1,1)*32767).astype(np.int16)
os.makedirs(os.path.dirname(OUT),exist_ok=True)
with wave.open(OUT,'w') as w:
    w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR); w.writeframes(pcm.tobytes())
print("wrote",OUT,f"{len(a)/SR:.3f}s")
