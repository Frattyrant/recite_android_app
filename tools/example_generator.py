"""Deterministic, category-aware manufacturing examples for built-in terms."""

from __future__ import annotations

import argparse
import copy
import hashlib
import json
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class ExamplePair:
    example_en: str
    example_zh: str


MECHANICAL_TEMPLATES = (
    ("The technician inspected the {term} before the production trial.", "技术员在试生产前检查了{meaning}。"),
    ("Please align the {term} with the reference datum.", "请将{meaning}与基准对齐。"),
    ("The team tightened the fasteners around the {term}.", "团队紧固了{meaning}周围的连接件。"),
    ("We verified the clearance around the {term} during commissioning.", "我们在调试期间确认了{meaning}周围的间隙。"),
    ("Clean the {term} during preventive maintenance.", "预防性维护时请清洁{meaning}。"),
    ("The inspector recorded the key dimension of the {term}.", "检验员记录了{meaning}的关键尺寸。"),
    ("Replace the worn {term} before restarting the line.", "产线重启前请更换磨损的{meaning}。"),
    ("Confirm that the {term} is installed in the correct orientation.", "请确认{meaning}的安装方向正确。"),
    ("The operator tested the movement of the {term} at low speed.", "操作员以低速测试了{meaning}的运动。"),
    ("Engineering approved the adjustment to the {term}.", "工程团队批准了对{meaning}的调整。"),
)

ELECTRICAL_TEMPLATES = (
    ("The electrician checked the {term} before powering the panel.", "电工在控制柜上电前检查了{meaning}。"),
    ("Verify the wiring to the {term} against the circuit diagram.", "请根据电路图核对{meaning}的接线。"),
    ("The controller received a stable signal from the {term}.", "控制器收到了来自{meaning}的稳定信号。"),
    ("Isolate power before replacing the {term}.", "更换{meaning}前请切断电源。"),
    ("The diagnostic screen reported a fault at the {term}.", "诊断界面报告了{meaning}处的故障。"),
    ("Confirm the {term} is connected to the correct terminal.", "请确认{meaning}连接到正确端子。"),
    ("The interlock stopped the circuit when the {term} failed.", "{meaning}发生故障时，联锁停止了电路。"),
    ("Measure the voltage at the {term} during commissioning.", "调试期间请测量{meaning}处的电压。"),
    ("Label the cable for the {term} inside the control panel.", "请标记控制柜内连接{meaning}的电缆。"),
    ("The maintenance team tested the {term} with power removed.", "维护团队在断电状态下测试了{meaning}。"),
)

GENERIC_TEMPLATES = (
    ("The team reviewed the {term} before starting work.", "团队在开工前确认了{meaning}。"),
    ("Please verify the {term} during the inspection.", "请在检查过程中确认{meaning}。"),
)


def _template_index(word: dict, count: int) -> int:
    raw_index = word.get("sourceIndex")
    try:
        source_index = int(raw_index)
    except (TypeError, ValueError):
        stable = str(word.get("id", "")).encode("utf-8")
        source_index = int.from_bytes(hashlib.sha256(stable).digest()[:4], "big")
    return (max(1, source_index) - 1) % count


def example_for(word: dict) -> ExamplePair:
    english = str(word.get("english", "")).strip()
    chinese = str(word.get("chinese", "")).strip()
    if str(word.get("kind", "TERM")).upper() == "PHRASE":
        return ExamplePair(english, chinese)

    term = str(word.get("primaryEnglish", "")).strip() or english
    category = str(word.get("category", "")).strip()
    templates = {
        "mechanical": MECHANICAL_TEMPLATES,
        "electrical": ELECTRICAL_TEMPLATES,
    }.get(category, GENERIC_TEMPLATES)
    primary_index = _template_index(word, len(templates))
    secondary_index = (primary_index + max(1, len(templates) // 2)) % len(templates)
    template_en, template_zh = templates[primary_index]
    secondary_en, secondary_zh = templates[secondary_index]
    return ExamplePair(
        example_en="\n".join(
            (
                template_en.format(term=term),
                secondary_en.format(term=term),
            ),
        ),
        example_zh="\n".join(
            (
                template_zh.format(meaning=chinese),
                secondary_zh.format(meaning=chinese),
            ),
        ),
    )


def apply_examples(content: dict) -> dict:
    updated = copy.deepcopy(content)
    for word in updated.get("words", []):
        example = example_for(word)
        word["exampleEn"] = example.example_en
        word["exampleZh"] = example.example_zh
    return updated


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--content", type=Path, required=True)
    args = parser.parse_args()
    content = json.loads(args.content.read_text(encoding="utf-8"))
    updated = apply_examples(content)
    args.content.write_text(
        json.dumps(updated, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(f"PASS examples={len(updated.get('words', []))}")


if __name__ == "__main__":
    main()
