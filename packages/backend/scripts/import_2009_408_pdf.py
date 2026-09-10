#!/usr/bin/env python3
"""Extract the 40 single-choice questions from the 2009 408 PDF into seed JSON."""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path
from pypdf import PdfReader

ANSWERS = "BCDBCBADABCDDCDCAADBDADDCACBAABABBCADDCA"
SUBJECTS = {**{n: "数据结构" for n in range(1, 11)},
            **{n: "计算机组成原理" for n in range(11, 23)},
            **{n: "操作系统" for n in range(23, 33)},
            **{n: "计算机网络" for n in range(33, 41)}}
TAGS = {
  1:["队列","缓冲区"],2:["栈","队列"],3:["二叉树遍历"],4:["平衡二叉树","二叉排序树"],
  5:["完全二叉树"],6:["森林与二叉树转换"],7:["无向图","顶点度"],8:["B树","B+树"],
  9:["堆","最小堆"],10:["排序算法","插入排序"],11:["指令周期","冯诺依曼结构"],
  12:["补码","类型转换"],13:["浮点数运算","溢出"],14:["Cache","组相联映射"],
  15:["存储器扩展","ROM","RAM"],16:["相对寻址","PC"],17:["RISC","CISC"],
  18:["指令流水线","时钟周期"],19:["硬布线控制器","微程序控制器"],20:["系统总线","总线带宽"],
  21:["Cache命中率"],22:["中断","外部中断"],23:["并行性","单处理机"],24:["进程调度","高响应比优先"],
  25:["死锁","资源分配"],26:["内存保护","界地址"],27:["分段存储","地址转换"],
  28:["文件物理结构","索引结构"],29:["磁盘调度","SCAN"],30:["文件控制块","访问控制"],
  31:["文件链接","引用计数"],32:["设备管理","逻辑设备名"],33:["OSI模型","传输层"],
  34:["奈奎斯特定理","QAM"],35:["GBN","滑动窗口"],36:["以太网交换机","MAC地址"],
  37:["CSMA/CD","最短帧长"],38:["TCP","确认序列号"],39:["TCP拥塞控制","拥塞窗口"],
  40:["FTP","控制连接"]
}
PAGE = {**{n:1 for n in range(1,10)}, **{n:2 for n in range(10,20)},
        **{n:3 for n in range(20,35)}, **{n:4 for n in range(35,41)}}

def clean(value: str) -> str:
    value = value.replace("\uf0ac", "←").replace("•", "·")
    value = re.sub(r"(?<=\w)\s+(?=H\b)", "", value)
    return re.sub(r"\s+", " ", value).strip()

def numbered_blocks(text: str, max_no: int | None = None):
    found = list(re.finditer(r"(?m)^\s*(\d{1,2})[\.．]\s*", text))
    for index, match in enumerate(found):
        no = int(match.group(1))
        if max_no is not None and not 1 <= no <= max_no:
            continue
        end = found[index + 1].start() if index + 1 < len(found) else len(text)
        yield no, text[match.end():end].strip()

def parse_questions(reader: PdfReader):
    text = "\n".join((page.extract_text() or "") for page in reader.pages[:4])
    text = text.split("二、综合应用题", 1)[0]
    result = {}
    for no, block in numbered_blocks(text, 40):
        markers = list(re.finditer(r"(?<![A-Za-z])([ABCD])\s*[\.．]\s*", block))
        if no == 4:
            result[no] = (clean(block), [{"id": x, "text": "见原题配图"} for x in "ABCD"])
            continue
        if len(markers) != 4:
            raise ValueError(f"question {no}: expected 4 options, got {len(markers)}")
        stem = clean(block[:markers[0].start()])
        options = []
        for i, marker in enumerate(markers):
            end = markers[i + 1].start() if i + 1 < len(markers) else len(block)
            options.append({"id": marker.group(1), "text": clean(block[marker.end():end])})
        result[no] = (stem, options)
    return result

def parse_explanations(reader: PdfReader):
    text = "\n".join((page.extract_text() or "") for page in reader.pages[12:17])
    explanations = {}
    for no, block in numbered_blocks(text, 40):
        explanations[no] = clean(block)
    return explanations

def main():
    if len(sys.argv) != 3:
        raise SystemExit("usage: import_2009_408_pdf.py INPUT.pdf OUTPUT.json")
    source, destination = Path(sys.argv[1]), Path(sys.argv[2])
    reader = PdfReader(source)
    questions, explanations = parse_questions(reader), parse_explanations(reader)
    if set(questions) != set(range(1, 41)):
        raise ValueError(f"question numbers incomplete: {sorted(set(range(1,41))-set(questions))}")
    if len(ANSWERS) != 40:
        raise ValueError(f"answer count is {len(ANSWERS)}, expected 40")
    records = []
    for no in range(1, 41):
        stem, options = questions[no]
        record = {
            "sourceType":"pdf_exam", "sourceDocument":"2009408.pdf", "sourceYear":2009,
            "sourceExam":"全国硕士研究生入学统一考试计算机学科专业基础综合",
            "sourceQuestionNo":no, "sourcePage":PAGE[no], "subject":SUBJECTS[no],
            "questionType":"single_choice", "questionText":stem, "options":options,
            "correctOptionId":ANSWERS[no-1], "explanation":explanations.get(no, ""),
            "knowledgeTags":TAGS[no], "assetPaths":[], "difficulty":2,
            "rawText":stem, "licenseStatus":"unverified",
            "reviewStatus":"pending_asset" if no in (3,4) else "ready"
        }
        if no in (3,4):
            record["assetPaths"] = [{"type":"pdf_page_region","source":"2009408.pdf","page":1,
                                     "note":f"第{no}题依赖原题配图，投放前需制作题目区域图片"}]
        records.append(record)
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(json.dumps(records, ensure_ascii=False, indent=2)+"\n", encoding="utf-8")
    print(f"wrote {len(records)} questions ({sum(x['reviewStatus']=='ready' for x in records)} ready) to {destination}")

if __name__ == "__main__":
    main()
