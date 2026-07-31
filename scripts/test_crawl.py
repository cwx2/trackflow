"""测试脚本：只爬 5 页，验证输出效果"""
import sys
import os
sys.path.insert(0, os.path.dirname(__file__))

import crawl_youtrack_docs as c

c.MAX_PAGES = 5
c.REQUEST_DELAY = 0.5

try:
    c.crawl()
    print("TEST_OK")
except Exception as e:
    import traceback
    traceback.print_exc()
    print(f"TEST_FAIL: {e}")
