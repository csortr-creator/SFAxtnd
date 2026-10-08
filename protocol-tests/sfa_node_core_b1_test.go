package libbox

import (
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"
)

func b1Find(paths ...string) string {
	for _, p := range paths {
		if st, err := os.Stat(p); err == nil && st.Size() > 0 {
			return p
		}
	}
	return ""
}

func b1WorkspaceCandidates(rel string) []string {
	var out []string
	if ws := os.Getenv("GITHUB_WORKSPACE"); ws != "" {
		out = append(out, filepath.Join(ws, rel))
	}
	if d := os.Getenv("SFA_B1_COMPARE_DIR"); d != "" && strings.Contains(rel, "node-core-compare") {
		out = append(out, d)
	}
	if c := os.Getenv("SFA_B1_BENCH_CONFIG"); c != "" && strings.Contains(rel, "harness-") {
		out = append(out, c)
	}
	out = append(out,
		filepath.Join("..", rel),
		filepath.Join("../..", rel),
		filepath.Join("protocol-tests", strings.TrimPrefix(rel, "protocol-tests/")),
		rel,
	)
	return out
}

func TestSFANodeCoreB1Bench(t *testing.T) {
	cfgPath := os.Getenv("SFA_B1_BENCH_CONFIG")
	if cfgPath == "" {
		cfgPath = b1Find(b1WorkspaceCandidates("protocol-tests/build/native-configs/harness-vless-reality-domain.json")...)
	}
	if cfgPath == "" {
		cfgPath = b1Find(b1WorkspaceCandidates("protocol-tests/build/node-core-compare/000-new.json")...)
	}
	if cfgPath == "" {
		t.Skip("no B1 bench config found")
	}
	raw, err := os.ReadFile(cfgPath)
	if err != nil {
		t.Fatalf("read config: %v", err)
	}
	content := string(raw)
	if err := CheckConfig(content); err != nil {
		t.Fatalf("control CheckConfig failed for %s: %v", cfgPath, err)
	}
	for _, n := range []int{1, 10, 100, 500} {
		start := time.Now()
		for i := 0; i < n; i++ {
			if err := CheckConfig(content); err != nil {
				t.Fatalf("check x%d iter %d: %v", n, i, err)
			}
		}
		elapsed := time.Since(start)
		t.Logf("in-process CheckConfig x%d total=%s avg=%s config=%s", n, elapsed, elapsed/time.Duration(n), filepath.Base(cfgPath))
	}
}

func TestSFANodeCoreB1DualCompare(t *testing.T) {
	dir := os.Getenv("SFA_B1_COMPARE_DIR")
	if dir == "" {
		dir = b1Find(b1WorkspaceCandidates("protocol-tests/build/node-core-compare")...)
	}
	if dir == "" {
		t.Skip("compare dir missing")
	}
	entries, err := os.ReadDir(dir)
	if err != nil {
		t.Skip("compare dir missing: ", err)
	}
	var newOnlyNode []string
	type row struct {
		index string
		oldOK bool
		newOK bool
		oldE  string
		newE  string
		seenO bool
		seenN bool
	}
	rows := map[string]*row{}
	for _, e := range entries {
		name := e.Name()
		if !strings.HasSuffix(name, ".json") || strings.HasPrefix(name, "broken-harness") {
			continue
		}
		raw, err := os.ReadFile(filepath.Join(dir, name))
		if err != nil {
			t.Fatal(err)
		}
		errC := CheckConfig(string(raw))
		ok := errC == nil
		errStr := ""
		if errC != nil {
			errStr = errC.Error()
		}
		var idx, side string
		if strings.HasSuffix(name, "-old.json") {
			idx = strings.TrimSuffix(name, "-old.json")
			side = "old"
		} else if strings.HasSuffix(name, "-new.json") {
			idx = strings.TrimSuffix(name, "-new.json")
			side = "new"
		} else {
			continue
		}
		r := rows[idx]
		if r == nil {
			r = &row{index: idx}
			rows[idx] = r
		}
		if side == "old" {
			r.oldOK, r.oldE, r.seenO = ok, errStr, true
		} else {
			r.newOK, r.newE, r.seenN = ok, errStr, true
		}
	}
	if len(rows) == 0 {
		t.Fatal("no compare pairs found in ", dir)
	}
	for _, r := range rows {
		if !r.seenO || !r.seenN {
			continue
		}
		t.Logf("index=%s oldOK=%v newOK=%v oldErr=%q newErr=%q", r.index, r.oldOK, r.newOK, trimErr(r.oldE), trimErr(r.newE))
		if r.oldOK && !r.newOK {
			low := strings.ToLower(r.newE)
			if strings.Contains(low, "unknown outbound") || strings.Contains(low, "missing required") {
				newOnlyNode = append(newOnlyNode, r.index)
			}
		}
	}
	if len(newOnlyNode) > 0 {
		t.Fatalf("NEW_ONLY_NODE indices=%v", newOnlyNode)
	}
}

func trimErr(s string) string {
	if len(s) > 120 {
		return s[:120] + "..."
	}
	return s
}

func TestSFANodeCoreB1BrokenHarness(t *testing.T) {
	dir := os.Getenv("SFA_B1_COMPARE_DIR")
	if dir == "" {
		dir = b1Find(b1WorkspaceCandidates("protocol-tests/build/node-core-compare")...)
	}
	if dir == "" {
		t.Skip("compare dir missing")
	}
	path := filepath.Join(dir, "broken-harness-new.json")
	raw, err := os.ReadFile(path)
	if err != nil {
		t.Skip(err)
	}
	if err := CheckConfig(string(raw)); err == nil {
		t.Fatal("broken harness must fail CheckConfig")
	} else {
		t.Logf("broken harness error (expected): %s", trimErr(err.Error()))
	}
}
