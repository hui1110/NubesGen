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
            {
                text: "Getting Started", items: [
                    {text: 'Python', link: 'https://learn.microsoft.com/azure/developer/python/'},
                    {text: '.NET', link: 'https://learn.microsoft.com/dotnet/azure/'},
                    {
                        text: 'JavaScript & Node.js',
                        link: 'https://learn.microsoft.com/azure/developer/javascript/'
                    },
                    {text: 'Java', link: 'https://learn.microsoft.com/azure/developer/java/'},
                    {text: 'Go', link: 'https://learn.microsoft.com/azure/developer/go/'}
                ]
            },
            {text: "Document", link: "/"},
            {text: "Changelog", link: "/"},
            {text: "more", link: "/"}
        ],
        displayAllHeaders: true,
        sidebar: [
            // {
            //     title: '📖 Azure Architecture Center',
            //     collapsable: false,
            //     sidebarDepth: 0,
            //     children: [
            //         ['https://learn.microsoft.com/azure/spring-apps/reference-architecture?tabs=azure-spring-standard', 'Reference architecture'],
            //         ['https://learn.microsoft.com/azure/architecture/reference-architectures/microservices/spring-apps-multi-region?toc=%2Fazure%2Fspring-apps%2Ftoc.json&bc=%2Fazure%2Fspring-apps%2Fbreadcrumb%2Ftoc.json', 'Deploy to multiple regions'],
            //     ],
            // },
            // {
            //     title: '🗜️ Command Line',
            //     collapsable: false,
            //     sidebarDepth: 0,
            //     children: [
            //         ['https://learn.microsoft.com/cli/azure/spring/app?view=azure-cli-latest', 'Azure CLI']
            //     ],
            // },
            {
                title: '📌 Deployment',
                collapsable: false,
                sidebarDepth: 0,
                children: [
                    ['/deploy-asa-button/create-asa-button', 'Azure Spring Apps Button'],
                    // ['https://learn.microsoft.com/cli/azure/spring/app?view=azure-cli-latest', 'Azure CLI'],
                    // ['https://learn.microsoft.com/azure/spring-apps/how-to-github-actions?pivots=programming-language-java', 'CI/CD with GitHub Actions'],
                    // ['https://learn.microsoft.com/azure/spring-apps/how-to-cicd?pivots=programming-language-java', 'CI/CD with Azure DevOps'],
                ],
            },
            // {
            //     title: '📊 Database',
            //     collapsable: false,
            //     sidebarDepth: 0,
            //     children: [
            //         ['https://learn.microsoft.com/azure/developer/java/spring-framework/deploy-passwordless-spring-database-app?toc=%2Fazure%2Fspring-apps%2Ftoc.json&bc=%2Fazure%2Fspring-apps%2Fbreadcrumb%2Ftoc.json&tabs=mysql', 'Passwordless connections to Azure databases'],
            //     ],
            // }, {
            //     title: '♾️ Monitoring & Mertrics',
            //     collapsable: false,
            //     sidebarDepth: 0,
            //     children: [
            //         ['https://learn.microsoft.com/azure/spring-apps/concepts-for-java-memory-management', 'Java memory management'],
            //         ['https://learn.microsoft.com/azure/spring-apps/concept-metrics', 'Metrics for Azure Spring Apps'],
            //     ],
            // },
            // {
            //     title: '🛡️ Security',
            //     collapsable: false,
            //     sidebarDepth: 0,
            //     children: [
            //         ['https://learn.microsoft.com/azure/spring-apps/concept-security-controls', 'Security controls'],
            //         ['https://learn.microsoft.com/security/benchmark/azure/baselines/azure-spring-apps-security-baseline?toc=%2Fazure%2Fspring-apps%2Ftoc.json&bc=%2Fazure%2Fspring-apps%2Fbreadcrumb%2Ftoc.json', 'Security baseline'],
            //         ['https://learn.microsoft.com/azure/spring-apps/security-controls-policy', 'Azure Policy Security controls'],
            //     ],
            // },
            // {
            //     title: '🐳 Azure Enterprise',
            //     collapsable: false,
            //     sidebarDepth: 0,
            //     children: [
            //         ['https://learn.microsoft.com/azure/spring-apps/quickstart-deploy-apps-enterprise', 'Build and deploy apps'],
            //     ],
            // },
            // {
            //     title: '✒️ Troubleshooting',
            //     collapsable: false,
            //     sidebarDepth: 0,
            //     children: [
            //         ['https://learn.microsoft.com/azure/spring-apps/troubleshoot', 'Troubleshoot common issues'],
            //     ],
            // },
            // {
            //     title: '🕔 Integrating with Salesforce',
            //     collapsable: false,
            //     sidebarDepth: 0,
            //     children: [
            //         ['https://learn.microsoft.com/azure/active-directory/saas-apps/salesforce-tutorial', 'Salesforce'],
            //         ['https://learn.microsoft.com/en-us/azure/active-directory/saas-apps/salesforce-sandbox-tutorial', 'Salesforce Sandbox'],
            //     ],
            // },
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
        ['script', {async: '', defer: '', src: 'https://buttons.github.io/buttons.js'}],
        ['script', {}, `(function(c,l,a,r,i,t,y){
        c[a]=c[a]||function(){(c[a].q=c[a].q||[]).push(arguments)};
        t=l.createElement(r);t.async=1;t.src="https://www.clarity.ms/tag/"+i;
        y=l.getElementsByTagName(r)[0];y.parentNode.insertBefore(t,y);
    })(window, document, "clarity", "script", "4zmkonp2tw");`]
    ]
};
