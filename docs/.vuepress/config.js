module.exports = {
    title: "NubesGen Documentation",
    description: "From code to deployment in minutes",
    themeConfig: {
        logo: 'assets/nubesgen-logo.svg',
        repo: 'microsoft/nubesgen',
        docsDir: 'docs',
        docsBranch: 'main',
        editLinks: true,
        lastUpdated: 'Last Updated',
        sidebarDepth: 1,
        smoothScroll: true,
        nav: [
         { text: "Home", link: '/' },
         { text: "Back to NubesGen.com", link: 'https://nubesgen.com' }
        ],
      displayAllHeaders: true,
      sidebar: [
          {
            title: '🔎 What is NubesGen?',
            path: 'NubesGen/what-is-nubesgen/overview/',
            collapsable: false,
            sidebarDepth: 0,
            children: [
              {
                title: 'Overview',
                path: 'NubesGen/what-is-nubesgen/overview',
              },
              'NubesGen/what-is-nubesgen/features',
              'NubesGen/what-is-nubesgen/philosophy',
              'NubesGen/what-is-nubesgen/roadmap',
              'NubesGen/what-is-nubesgen/telemetry',
              'NubesGen/what-is-nubesgen/contact',
            ],
          },
          {
            title: '🚀 Getting started',
            collapsable: false,
            sidebarDepth: 0,
            children: [
              'NubesGen/getting-started/terraform',
              'NubesGen/getting-started/bicep',
              'NubesGen/getting-started/gitops',
              'NubesGen/getting-started/cli',
            ],
          },
          {
            title: '⌨️ Runtimes support',
            collapsable: false,
            sidebarDepth: 0,
            children: [
              'NubesGen/runtimes/docker',
              'NubesGen/runtimes/dot-net',
              'NubesGen/runtimes/java',
              'NubesGen/runtimes/spring-boot',
              'NubesGen/runtimes/quarkus',
              'NubesGen/runtimes/micronaut',
              'NubesGen/runtimes/nodejs',
              'NubesGen/runtimes/python',
            ],
          },
          {
            title: '🪅 GitOps',
            collapsable: false,
            sidebarDepth: 0,
            children: [
              'NubesGen/gitops/gitops-overview',
              'NubesGen/gitops/gitops-quick-start',
            ],
          },
          {
            title: '👐 Contributing',
            path: 'NubesGen/contributing/contributing',
            collapsable: true,
            sidebarDepth: 0,
            children: [
              'NubesGen/contributing/contributing',
              'NubesGen/contributing/bug-report',
              'NubesGen/contributing/feature-request',
              'NubesGen/contributing/documentation',
            ],
          },
          {
            title: '📚 Reference',
            collapsable: true,
            sidebarDepth: 0,
            children: [
              'NubesGen/reference/frequently-asked-questions',
              'NubesGen/reference/troubleshooting',
              'NubesGen/reference/rest-api',
              'NubesGen/reference/what-is-being-generated',
            ],
          },
          {
            title: '✨ Community content',
            path: 'NubesGen/community-content'
          }
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
