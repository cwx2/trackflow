import sys

keyword = sys.argv[1] if len(sys.argv) > 1 else "consumer-2"
n = int(sys.argv[2]) if len(sys.argv) > 2 else 80

with open("scripts/log/auto_iterate_parallel.log", encoding="utf-8", errors="replace") as f:
    lines = f.readlines()

matches = [l.rstrip() for l in lines if keyword in l]
print(f"共找到 {len(matches)} 行包含 '{keyword}'，显示最后 {n} 行：")
print("\n".join(matches[-n:]))
