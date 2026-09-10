import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import idea_bridge


class IdeaBridgeTest(unittest.TestCase):
    def test_opens_only_a_target_inside_the_allowlisted_project(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            project = root / "course-lab"
            source = project / "src" / "Main.java"
            source.parent.mkdir(parents=True)
            source.write_text("class Main {}", encoding="utf-8")
            idea = root / "idea"
            idea.write_text("", encoding="utf-8")
            config = root / "config.json"
            config.write_text(json.dumps({"ideaCommand": str(idea), "allowedRoots": [str(root)]}))

            with patch.object(idea_bridge, "CONFIG_PATH", config), patch.object(idea_bridge.subprocess, "Popen") as popen:
                result = idea_bridge.handle({"action": "open", "projectPath": str(project), "filePath": "src/Main.java"})

            self.assertTrue(result["success"])
            popen.assert_called_once()

    def test_rejects_a_project_outside_the_allowlist(self):
        with tempfile.TemporaryDirectory() as allowed, tempfile.TemporaryDirectory() as outside:
            config = Path(allowed) / "config.json"
            config.write_text(json.dumps({"ideaCommand": "/bin/echo", "allowedRoots": [allowed]}))
            with patch.object(idea_bridge, "CONFIG_PATH", config):
                with self.assertRaisesRegex(ValueError, "outside allowedRoots"):
                    idea_bridge.handle({"action": "open", "projectPath": outside})


if __name__ == "__main__":
    unittest.main()
