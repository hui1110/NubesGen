(window.webpackJsonp=window.webpackJsonp||[]).push([[13],{413:function(t,e,a){"use strict";a.r(e);var s=a(56),r=Object(s.a)({},(function(){var t=this,e=t.$createElement,a=t._self._c||e;return a("ContentSlotsDistributor",{attrs:{"slot-key":t.$parent.slotKey}},[a("h1",{attrs:{id:"creating-a-deploy-to-azure-button"}},[a("a",{staticClass:"header-anchor",attrs:{href:"#creating-a-deploy-to-azure-button"}},[t._v("#")]),t._v(" Creating a Deploy to Azure Button")]),t._v(" "),a("p",[a("strong",[t._v("Table of Contents:")])]),t._v(" "),a("p"),a("div",{staticClass:"table-of-contents"},[a("ul",[a("li",[a("a",{attrs:{href:"#requirements"}},[t._v("Requirements")])]),a("li",[a("a",{attrs:{href:"#button-terms-of-use"}},[t._v("Button Terms of Use")])]),a("li",[a("a",{attrs:{href:"#adding-the-azure-button"}},[t._v("Adding the Azure button")]),a("ul",[a("li",[a("a",{attrs:{href:"#button-image"}},[t._v("Button image")])])])]),a("li",[a("a",{attrs:{href:"#using-a-custom-git-branch"}},[t._v("Using a custom Git branch")])]),a("li",[a("a",{attrs:{href:"#further-reading"}},[t._v("Further reading")])]),a("li",[a("a",{attrs:{href:"#📑-keep-reading"}},[t._v("📑 Keep reading")])]),a("li",[a("a",{attrs:{href:"#⚡-feedback"}},[t._v("⚡ Feedback")])])])]),a("p"),t._v(" "),a("p",[t._v("The "),a("em",[t._v("Deploy to Azure")]),t._v(" button enables users to deploy apps to Azure Spring Apps without leaving the web browser and with little or no configuration. The button is ideal for customers, open-source project maintainers, or add-on providers who wish to provide their customers with a quick and easy way to deploy application to Azure Spring Apps.")]),t._v(" "),a("p",[t._v("The basic requirement of the creation button is that your application source code hosting is in the GitHub repository. We will add deployment buttons to the "),a("code",[t._v("README.md")]),t._v(" file.")]),t._v(" "),a("p",[t._v("Here’s an example button that deploys a sample to Azure Spring Apps:")]),t._v(" "),a("p",[a("a",{attrs:{href:""}},[a("img",{attrs:{src:"https://user-images.githubusercontent.com/58474919/236122963-8c0857bb-3822-4485-892a-445fa33f1612.png",alt:"Deploy to Azure"}})])]),t._v(" "),a("p",[t._v("This document describes the requirements for apps that use the "),a("code",[t._v("Deploy to Azure")]),t._v(" service, and how to use these buttons make it easy to deploy source code you maintain to Azure Spring Apps.")]),t._v(" "),a("h2",{attrs:{id:"requirements"}},[a("a",{staticClass:"header-anchor",attrs:{href:"#requirements"}},[t._v("#")]),t._v(" Requirements")]),t._v(" "),a("ul",[a("li",[t._v("The application source code is hosted in the GitHub public repository.")]),t._v(" "),a("li",[t._v("An Azure AD user with admin permission.")])]),t._v(" "),a("h2",{attrs:{id:"button-terms-of-use"}},[a("a",{staticClass:"header-anchor",attrs:{href:"#button-terms-of-use"}},[t._v("#")]),t._v(" Button Terms of Use")]),t._v(" "),a("p",[t._v("The Azure Terms of Use (Default) governs the Terms of Use of your button unless you provide your own Terms of Use in your GitHub repository. It is common practice to link to your Terms of Use in your README file or to add them as a license file to your GitHub repository.")]),t._v(" "),a("h2",{attrs:{id:"adding-the-azure-button"}},[a("a",{staticClass:"header-anchor",attrs:{href:"#adding-the-azure-button"}},[t._v("#")]),t._v(" Adding the Azure button")]),t._v(" "),a("p",[t._v("The following is an example that changes the template query parameter to the "),a("code",[t._v("URL")]),t._v(" of the repository:")]),t._v(" "),a("div",{staticClass:"custom-block tip"},[a("p",{staticClass:"custom-block-title"},[t._v("TIP")]),t._v(" "),a("p",[t._v("When adding only the "),a("code",[t._v("url")]),t._v(", Azure Button will pull the source code from the default branch of your GitHub repository.")])]),t._v(" "),a("div",{staticClass:"language-markdown extra-class"},[a("pre",{pre:!0,attrs:{class:"language-markdown"}},[a("code",[a("span",{pre:!0,attrs:{class:"token url"}},[t._v("["),a("span",{pre:!0,attrs:{class:"token content"}},[t._v("![Deploy to Azure")]),t._v("]("),a("span",{pre:!0,attrs:{class:"token url"}},[t._v("https://user-images.githubusercontent.com/58474919/236122963-8c0857bb-3822-4485-892a-445fa33f1612.png")]),t._v(")")]),t._v("](https://azure.spring.launcher.com/deploy.html?url=https://github.com/azure/deploy)\n")])])]),a("p",[t._v("Here’s the equivalent content as HTML if you’d prefer not to use Markdown:")]),t._v(" "),a("div",{staticClass:"language-html extra-class"},[a("pre",{pre:!0,attrs:{class:"language-html"}},[a("code",[a("span",{pre:!0,attrs:{class:"token tag"}},[a("span",{pre:!0,attrs:{class:"token tag"}},[a("span",{pre:!0,attrs:{class:"token punctuation"}},[t._v("<")]),t._v("a")]),t._v(" "),a("span",{pre:!0,attrs:{class:"token attr-name"}},[t._v("href")]),a("span",{pre:!0,attrs:{class:"token attr-value"}},[a("span",{pre:!0,attrs:{class:"token punctuation attr-equals"}},[t._v("=")]),a("span",{pre:!0,attrs:{class:"token punctuation"}},[t._v('"')]),t._v("https://azure.spring.launcher.com/deploy.html?url=https://github.com/azure/deploy"),a("span",{pre:!0,attrs:{class:"token punctuation"}},[t._v('"')])]),a("span",{pre:!0,attrs:{class:"token punctuation"}},[t._v(">")])]),t._v("\n  "),a("span",{pre:!0,attrs:{class:"token tag"}},[a("span",{pre:!0,attrs:{class:"token tag"}},[a("span",{pre:!0,attrs:{class:"token punctuation"}},[t._v("<")]),t._v("img")]),t._v(" "),a("span",{pre:!0,attrs:{class:"token attr-name"}},[t._v("src")]),a("span",{pre:!0,attrs:{class:"token attr-value"}},[a("span",{pre:!0,attrs:{class:"token punctuation attr-equals"}},[t._v("=")]),a("span",{pre:!0,attrs:{class:"token punctuation"}},[t._v('"')]),t._v("https://user-images.githubusercontent.com/58474919/236122963-8c0857bb-3822-4485-892a-445fa33f1612.png"),a("span",{pre:!0,attrs:{class:"token punctuation"}},[t._v('"')])]),t._v(" "),a("span",{pre:!0,attrs:{class:"token attr-name"}},[t._v("alt")]),a("span",{pre:!0,attrs:{class:"token attr-value"}},[a("span",{pre:!0,attrs:{class:"token punctuation attr-equals"}},[t._v("=")]),a("span",{pre:!0,attrs:{class:"token punctuation"}},[t._v('"')]),t._v("Deploy to Azure"),a("span",{pre:!0,attrs:{class:"token punctuation"}},[t._v('"')])]),a("span",{pre:!0,attrs:{class:"token punctuation"}},[t._v(">")])]),t._v("\n"),a("span",{pre:!0,attrs:{class:"token tag"}},[a("span",{pre:!0,attrs:{class:"token tag"}},[a("span",{pre:!0,attrs:{class:"token punctuation"}},[t._v("</")]),t._v("a")]),a("span",{pre:!0,attrs:{class:"token punctuation"}},[t._v(">")])]),t._v("\n")])])]),a("h3",{attrs:{id:"button-image"}},[a("a",{staticClass:"header-anchor",attrs:{href:"#button-image"}},[t._v("#")]),t._v(" Button image")]),t._v(" "),a("p",[t._v("When linking to the Azure Button set-up flow, you can either use a raw link or an image link. If using an image, Azure makes available SVG versions at these URLs:")]),t._v(" "),a("div",{staticClass:"language-bash extra-class"},[a("pre",{pre:!0,attrs:{class:"language-bash"}},[a("code",[t._v("https://user-images.githubusercontent.com/58474919/236122963-8c0857bb-3822-4485-892a-445fa33f1612.png\n")])])]),a("h2",{attrs:{id:"using-a-custom-git-branch"}},[a("a",{staticClass:"header-anchor",attrs:{href:"#using-a-custom-git-branch"}},[t._v("#")]),t._v(" Using a custom Git branch")]),t._v(" "),a("p",[t._v("If you’d like the button to deploy from a specific Git branch, you can use a fully qualified GitHub URL as the branch parameter:")]),t._v(" "),a("div",{staticClass:"language-bash extra-class"},[a("pre",{pre:!0,attrs:{class:"language-bash"}},[a("code",[t._v("https://azure.spring.launcher.com/deploy.html?url"),a("span",{pre:!0,attrs:{class:"token operator"}},[t._v("=")]),t._v("https://github.com/azure/deploy"),a("span",{pre:!0,attrs:{class:"token operator"}},[t._v("&")]),a("span",{pre:!0,attrs:{class:"token assign-left variable"}},[t._v("branch")]),a("span",{pre:!0,attrs:{class:"token operator"}},[t._v("=")]),t._v("main\n")])])]),a("h2",{attrs:{id:"further-reading"}},[a("a",{staticClass:"header-anchor",attrs:{href:"#further-reading"}},[t._v("#")]),t._v(" Further reading")]),t._v(" "),a("p",[t._v("For more detailed information about see the following documents:")]),t._v(" "),a("ul",[a("li",[a("a",{attrs:{href:"https://learn.microsoft.com/rest/api/azure/",target:"_blank",rel:"noopener noreferrer"}},[t._v("Azure Platform API Reference: App Setups"),a("OutboundLink")],1)])]),t._v(" "),a("h2",{attrs:{id:"📑-keep-reading"}},[a("a",{staticClass:"header-anchor",attrs:{href:"#📑-keep-reading"}},[t._v("#")]),t._v(" 📑 Keep reading")]),t._v(" "),a("p",[t._v("📓 "),a("a",{attrs:{href:"https://azure.microsoft.com/solutions/integration-services",target:"_blank",rel:"noopener noreferrer"}},[t._v("Deployment Integrations"),a("OutboundLink")],1),t._v(".")]),t._v(" "),a("h2",{attrs:{id:"⚡-feedback"}},[a("a",{staticClass:"header-anchor",attrs:{href:"#⚡-feedback"}},[t._v("#")]),t._v(" ⚡ Feedback")]),t._v(" "),a("p",[t._v("◀️ "),a("a",{attrs:{href:"https://github.com/",target:"_blank",rel:"noopener noreferrer"}},[t._v("Log in to submit feedback"),a("OutboundLink")],1),t._v(".")])])}),[],!1,null,null,null);e.default=r.exports}}]);