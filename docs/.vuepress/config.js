module.exports = {
    title: "Azure Spring Apps Button Documentation",
    base: "/NubesGen/",
    description: "From code to deployment in minutes",
    themeConfig: {
        logo: '/assets/Microsoft_Azure.svg',
        repo: 'hui1110/nubesgen',
        docsDir: 'docs',
        docsBranch: 'main',
        editLinks: true,
        lastUpdated: 'Last Updated',
        sidebarDepth: 1,
        smoothScroll: true,
        nav: [
         { text: "Getting Started", link: "/"},
         { text: "Document", link: "/"},
         { text: "Changelog", link: "/"},
         { text: "more", link: "/"}
        ],
      displayAllHeaders: true,
      sidebar: [
          {
            title: '📖 Azure Architecture',
            collapsable: true,
            sidebarDepth: 0,
          },
          {
              title: '⌨️ Azure Spring Apps Button',
              collapsable: false,
              sidebarDepth: 0,
              children: [
                  '/deploy-asa-button/create-asa-button',
              ],
          },
          {
              title: '👐 Command Line',
              collapsable: true,
              sidebarDepth: 0,
          },
          {
              title: '🪅 Deployment',
              collapsable: true,
              sidebarDepth: 0,
          },
          {
              title: '📚 Continuous Delivery',
              collapsable: true,
              sidebarDepth: 0,
          },
          {
              title: '✨ Database & Data Management',
              collapsable: true,
              sidebarDepth: 0,
          },{
              title: '🐛 Monitoring & Mertrics',
              collapsable: true,
              sidebarDepth: 0,
          },{
              title: '💡 Add-ons',
              collapsable: true,
              sidebarDepth: 0,
          },
          {
              title: '🚀 Collaboration',
              collapsable: true,
              sidebarDepth: 0,
          },
          {
              title: '🚀 Security',
              collapsable: true,
              sidebarDepth: 0,
          },
          {
              title: '🚀 Azure Enterprise',
              collapsable: true,
              sidebarDepth: 0,
          },
          {
              title: '🚀 Patterns & Best Practices',
              collapsable: true,
              sidebarDepth: 0,
          },
          {
              title: '🚀 Extending Azure',
              collapsable: true,
              sidebarDepth: 0,
          },
          {
              title: '🚀 Account & Building',
              collapsable: true,
              sidebarDepth: 0,
          },
          {
              title: '🚀 Troubleshooting & Support',
              collapsable: true,
              sidebarDepth: 0,
          },
          {
              title: '🚀 Integrating with Salesforce',
              collapsable: true,
              sidebarDepth: 0,
          },
      ]
    },
    plugins: [
      [
        'vuepress-plugin-clean-urls',
        {
          normalSuffix: '/',
          indexSuffix: '/',
          notFoundPath: '/404.html',
        },
      ],
    ],
    head: [
      ['script', { async: '', defer: '' , src: 'https://buttons.github.io/buttons.js' }],
      ['script', {}, `(function(c,l,a,r,i,t,y){
        c[a]=c[a]||function(){(c[a].q=c[a].q||[]).push(arguments)};
        t=l.createElement(r);t.async=1;t.src="https://www.clarity.ms/tag/"+i;
        y=l.getElementsByTagName(r)[0];y.parentNode.insertBefore(t,y);
    })(window, document, "clarity", "script", "4zmkonp2tw");` ]
    ]
  };
