package libbox

import (
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"
)

// TestSFANodeCoreB1Bench measures in-process CheckConfig cost (no network).
// Failures are functional only; timings are logged and never fail the test.
func TestSFANodeCoreB1Bench(t *testing.T) {
	cfgPath := os.Getenv("SFA_B1_BENCH_CONFIG")
	if cfgPath == "" {
		// Prefer harness fixture emitted by protocol-tests if present.
		candidates := []string{
			"../protocol-tests/build/native-configs/harness-vless-reality-domain.json",
			"../protocol-tests/build/node-core-compare/000-new.json",
		}
		for _, c := range candidates {
			if st, err := os.Stat(c); err == nil && st.Size() > 0 {
				cfgPath = c
				break
			}
		}
	}
	if cfgPath == "" {
		t.Skip("no B1 bench config; run protocol-tests first or set SFA_B1_BENCH_CONFIG")
	}
	raw, err := os.ReadFile(cfgPath)
	if err != nil {
		t.Fatalf("read config: %v", err)
	}
	content := string(raw)
	if err := CheckConfig(content); err != nil {
		t.Fatalf("control CheckConfig failed: %v", err)
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

// TestSFANodeCoreB1DualCompare runs old vs new JSON pairs if SFA_B1_COMPARE_DIR is set.
func TestSFANodeCoreB1DualCompare(t *testing.T) {
	dir := os.Getenv("SFA_B1_COMPARE_DIR")
	if dir == "" {
		dir = "../protocol-tests/build/node-core-compare"
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
	}
	rows := map[string]*row{}
	for _, e := range entries {
		name := e.Name()
		if !strings.HasSuffix(name, ".json") {
			continue
		}
		if strings.HasPrefix(name, "broken-harness") {
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
			r.oldOK = ok
			r.oldE = errStr
		} else {
			r.newOK = ok
			r.newE = errStr
		}
	}
	for _, r := range rows {
		t.Logf("index=%s oldOK=%v newOK=%v oldErr=%q newErr=%q", r.index, r.oldOK, r.newOK, trimErr(r.oldE), trimErr(r.newE))
		if r.oldOK && !r.newOK {
			// Potential NEW_ONLY_NODE — only fail if error looks like outbound type/schema, not harness
			low := strings.ToLower(r.newE)
			if strings.Contains(low, "unknown outbound") || strings.Contains(low, "missing required") {
				newOnlyNode = append(newOnlyNode, r.index)
			}
		}
	}
	if len(newOnlyNode) > 0 {
		t.Fatalf("NEW_ONLY_NODE indices=%v", newOnlyNode)
	}
	// Family controls: at least reality domain new config must pass
	if r := rows["000"]; r != nil && !r.newOK {
		t.Fatalf("control index 000 new harness must PASS: %s", r.newE)
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
		dir = "../protocol-tests/build/node-core-compare"
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
