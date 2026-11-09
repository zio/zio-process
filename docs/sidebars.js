const sidebars = {
  sidebar: [
    {
      type: "category",
      label: "ZIO Process",
      collapsed: false,
      link: { type: "doc", id: "index" },
      items: [
        "getting-started",
        {
          type: "category",
          label: "Guides",
          collapsed: false,
          items: [
            "running-commands",
            "input-output",
            "piping",
            "interactive-processes",
            "process-control",
            "platform-support"
          ]
        },
        "reference"
      ]
    }
  ]
};

module.exports = sidebars;
