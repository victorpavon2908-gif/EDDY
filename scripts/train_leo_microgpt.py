#!/usr/bin/env python3
"""Train Leo MicroGPT v1 and export the INT8 checkpoint used by the Android pure-Kotlin runtime."""
from __future__ import annotations
import argparse, base64, hashlib, json, math, random, re, struct, unicodedata
from collections import Counter
from pathlib import Path
import torch
import torch.nn as nn
import torch.nn.functional as F

SEED = 2908
CONTEXT = 40
DIM = 64
HEADS = 4
FF = 128
LAYERS = 2
BATCH = 64
TOKEN_RE = re.compile(r"[^\W_]+(?:'[^\W_]+)?|\d+|[¿?¡!.,;:]", re.UNICODE)
FILLER_PREFIX = ["", "leo ", "oye leo ", "ey leo "]
FILLER_SUFFIX = ["", " porfa", " por favor", " vale", " va", " pues"]

def strip_accents(s: str) -> str:
    return "".join(c for c in unicodedata.normalize("NFD", s.lower()) if unicodedata.category(c) != "Mn")

def prompt_tok(text: str) -> list[str]:
    return TOKEN_RE.findall(strip_accents(text))

def response_tok(text: str) -> list[str]:
    return TOKEN_RE.findall(text.lower())

class Block(nn.Module):
    def __init__(self):
        super().__init__()
        self.ln1 = nn.LayerNorm(DIM)
        self.q = nn.Linear(DIM, DIM); self.k = nn.Linear(DIM, DIM)
        self.v = nn.Linear(DIM, DIM); self.o = nn.Linear(DIM, DIM)
        self.ln2 = nn.LayerNorm(DIM)
        self.fc1 = nn.Linear(DIM, FF); self.fc2 = nn.Linear(FF, DIM)
        self.drop = nn.Dropout(0.08)
    def forward(self, x):
        b, t, d = x.shape
        y = self.ln1(x)
        hd = DIM // HEADS
        q = self.q(y).view(b,t,HEADS,hd).transpose(1,2)
        k = self.k(y).view(b,t,HEADS,hd).transpose(1,2)
        v = self.v(y).view(b,t,HEADS,hd).transpose(1,2)
        att = (q @ k.transpose(-2,-1)) / math.sqrt(hd)
        mask = torch.triu(torch.ones(t,t,device=x.device,dtype=torch.bool), 1)
        att = F.softmax(att.masked_fill(mask, float("-inf")), dim=-1)
        z = (att @ v).transpose(1,2).contiguous().view(b,t,d)
        x = x + self.drop(self.o(z))
        y = self.ln2(x)
        return x + self.drop(self.fc2(F.gelu(self.fc1(y))))

class MicroGPT(nn.Module):
    def __init__(self, vocab_size: int):
        super().__init__()
        self.tok = nn.Embedding(vocab_size, DIM)
        self.pos = nn.Embedding(CONTEXT, DIM)
        self.blocks = nn.ModuleList([Block() for _ in range(LAYERS)])
        self.lnf = nn.LayerNorm(DIM)
        self.bias = nn.Parameter(torch.zeros(vocab_size))
    def forward(self, idx):
        _, t = idx.shape
        x = self.tok(idx) + self.pos(torch.arange(t, device=idx.device))[None,:,:]
        for block in self.blocks: x = block(x)
        x = self.lnf(x)
        return x @ self.tok.weight.T + self.bias

def build_vocab(corpus):
    keys = list(corpus)
    family_tokens = [f"<f_{key}>" for key in keys]
    special = ["<pad>","<bos>","<sep>","<eos>","<unk>"] + family_tokens
    words = []
    for family in corpus.values():
        for prompt in family["prompts"]: words += prompt_tok(prompt)
        for response in family["responses"]: words += response_tok(response)
    for value in FILLER_PREFIX + FILLER_SUFFIX: words += prompt_tok(value)
    return special + [word for word,_ in Counter(words).most_common() if word not in special]

def train(corpus):
    random.seed(SEED); torch.manual_seed(SEED); torch.set_num_threads(4)
    keys = list(corpus)
    vocab = build_vocab(corpus); stoi = {v:i for i,v in enumerate(vocab)}
    pad, sep, unk = stoi["<pad>"], stoi["<sep>"], stoi["<unk>"]
    def augment(prompt):
        if random.random() < 0.28: prompt = random.choice(FILLER_PREFIX[1:]) + prompt
        if random.random() < 0.30: prompt = prompt + random.choice(FILLER_SUFFIX[1:])
        if random.random() < 0.15: prompt += "?"
        return prompt
    def encode(key,prompt,response):
        return [stoi["<bos>"], stoi[f"<f_{key}>"]] + [stoi.get(w,unk) for w in prompt_tok(prompt)] + [sep] + [stoi.get(w,unk) for w in response_tok(response)] + [stoi["<eos>"]]
    def batch():
        xs=[]; ys=[]; masks=[]
        for _ in range(BATCH):
            key=random.choice(keys); family=corpus[key]
            ids=encode(key, augment(random.choice(family["prompts"])), random.choice(family["responses"]))[:CONTEXT]
            x=ids[:-1]; y=ids[1:]; sep_index=ids.index(sep)
            mask=[1.0 if i >= sep_index else 0.0 for i in range(len(y))]
            n=(CONTEXT-1)-len(x)
            xs.append(x+[pad]*n); ys.append(y+[pad]*n); masks.append(mask+[0.0]*n)
        return torch.tensor(xs), torch.tensor(ys), torch.tensor(masks)
    model=MicroGPT(len(vocab))
    stages=[(480,2.7e-3,0.01,True),(220,3e-4,0.005,False),(320,2e-4,0.003,False)]
    final_losses=[]
    for steps,lr,decay,cosine in stages:
        opt=torch.optim.AdamW(model.parameters(),lr=lr,weight_decay=decay)
        sched=torch.optim.lr_scheduler.CosineAnnealingLR(opt,T_max=steps,eta_min=2e-4) if cosine else None
        for _ in range(steps):
            x,y,m=batch(); logits=model(x)
            losses=F.cross_entropy(logits.reshape(-1,len(vocab)),y.reshape(-1),reduction="none").view_as(y)
            loss=(losses*m).sum()/m.sum()
            opt.zero_grad(set_to_none=True); loss.backward()
            torch.nn.utils.clip_grad_norm_(model.parameters(),1.0); opt.step()
            if sched: sched.step()
            final_losses.append(float(loss))
    return model.eval().cpu(), vocab, sum(final_losses[-50:])/50

def export_int8(model, vocab, output: Path):
    with output.open("wb") as f:
        f.write(b"LEOMGQ81")
        f.write(struct.pack("<7I",1,len(vocab),CONTEXT,DIM,HEADS,FF,LAYERS))
        for token in vocab:
            raw=token.encode("utf-8"); f.write(struct.pack("<H",len(raw))); f.write(raw)
        tensors=[model.tok.weight, model.pos.weight]
        for b in model.blocks:
            tensors += [b.ln1.weight,b.ln1.bias,b.q.weight,b.q.bias,b.k.weight,b.k.bias,b.v.weight,b.v.bias,b.o.weight,b.o.bias,b.ln2.weight,b.ln2.bias,b.fc1.weight,b.fc1.bias,b.fc2.weight,b.fc2.bias]
        tensors += [model.lnf.weight, model.lnf.bias, model.bias]
        for tensor in tensors:
            value=tensor.detach().cpu()
            maximum=float(value.abs().max()); scale=maximum/127.0 if maximum>0 else 1.0
            quantized=torch.clamp(torch.round(value/scale),-127,127).to(torch.int8).numpy()
            f.write(struct.pack("<f",scale)); f.write(quantized.tobytes())
    data=output.read_bytes()
    return hashlib.sha256(data).hexdigest()

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument("--corpus", default="scripts/leo_microgpt_corpus.json")
    parser.add_argument("--output", default="app/src/main/assets/leo-microgpt-v1.bundle")
    parser.add_argument("--base64-parts", type=int, default=0, help="Optionally write N .b64.part files instead of a raw asset.")
    args=parser.parse_args()
    corpus=json.loads(Path(args.corpus).read_text(encoding="utf-8"))
    model,vocab,loss=train(corpus)
    output=Path(args.output); output.parent.mkdir(parents=True,exist_ok=True)
    sha=export_int8(model,vocab,output)
    print(f"parameters={sum(p.numel() for p in model.parameters())}")
    print(f"vocab={len(vocab)} families={len(corpus)} avg_last_50_loss={loss:.6f}")
    print(f"bytes={output.stat().st_size} sha256={sha}")
    if args.base64_parts:
        encoded=base64.b64encode(output.read_bytes()).decode("ascii")
        chunk=(len(encoded)+args.base64_parts-1)//args.base64_parts
        for i in range(args.base64_parts):
            Path(f"{output}.b64.part{i+1}").write_text(encoded[i*chunk:(i+1)*chunk],encoding="ascii")

if __name__ == "__main__":
    main()
