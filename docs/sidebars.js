const sidebars = {
  sidebar: [
    "index",
    "getting-started",
    {
      type: "category",
      label: "Concepts",
      collapsed: false,
      link: { type: "doc", id: "concepts/index" },
      items: [
        "concepts/commands-and-processes",
        "concepts/streams-and-redirects",
        "concepts/errors-and-exit-codes",
        "platform-support"
      ]
    },
    {
      type: "category",
      label: "Reference",
      collapsed: false,
      items: [
        "reference/command",
        "reference/process",
        "reference/process-input-output",
        "reference/command-error"
      ]
    },
    "tutorial-log-search-cli",
    {
      type: "category",
      label: "How-to Guides",
      collapsed: true,
      items: [
        "how-to-run-a-command-and-read-its-output",
        "how-to-read-standard-output-and-error-separately",
        "how-to-set-the-environment-and-working-directory",
        "how-to-feed-input-to-a-program",
        "how-to-redirect-output-to-a-file",
        "piping",
        "interactive-processes",
        "how-to-run-commands-in-parallel",
        "how-to-run-a-shell-script",
        "how-to-stop-a-running-process",
        "how-to-set-a-timeout-on-a-command",
        "how-to-handle-command-errors",
        "how-to-migrate-from-scala-sys-process"
      ]
    }
  ]
};

module.exports = sidebars;
