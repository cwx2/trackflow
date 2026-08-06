import sys

keyword = sys.argv[1]
n = int(sys.argv[2])

with open("scripts/log/auto_iterate_parallel.log", encoding="utf-8", errors="replace") as f:
    lines = f.readlines()

# 找包含 keyword 但不包含 "-stdout" 的行（worker 主日志）
matches = [l.rstrip() for l in lines if keyword in l and "-stdout" not in l]
print(f"共 {len(matches)} 行，显示最后 {n} 行：")
print("\n".join(matches[-n:]))
