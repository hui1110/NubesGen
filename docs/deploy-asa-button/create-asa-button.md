# Create a Deploy to Azure Button

__Table of Contents:__

[[toc]]

The `Deploy to Azure Spring Apps` button enables users to deploy applicatioin to Azure Spring Apps without leaving the web browser and with little or no configuration. The button is ideal for customers, open-source project maintainers, or add-on providers who wish to provide their customers with a quick and easy way to deploy application to Azure Spring Apps.

The basic requirement of the creation button is that your application source code hosting is in the GitHub repository. We will add deploy button to the `README.md` file.

Here’s an example button that deploys a sample to Azure Spring Apps:

[![Deploy to Azure Spring Apps](https://user-images.githubusercontent.com/58474919/236122963-8c0857bb-3822-4485-892a-445fa33f1612.png)]()

This document describes the requirements for apps that use the `Deploy to Azure Spring Apps` service, and how to use these buttons make it easy to deploy source code you maintain to Azure Spring Apps.

## Requirements

- The application source code is hosted in the GitHub public repository.
- An Azure AD user with admin permission.

## Button Terms of Use

The Azure Terms of Use (Default) governs the Terms of Use of your button unless you provide your own Terms of Use in your GitHub repository. It is common practice to link to your Terms of Use in your README file or to add them as a license file to your GitHub repository.

## Adding the Azure button

The following is an example that changes the template query parameter to the `url` of the repository:

:::tip
When adding only the `url`, Azure Button will pull the source code from the default branch of your GitHub repository.
:::

```markdown
[![Deploy to Azure](https://user-images.githubusercontent.com/58474919/236122963-8c0857bb-3822-4485-892a-445fa33f1612.png)](https://azure.spring.launcher.com/deploy.html?url=https://github.com/azure/deploy)
```

Here’s the equivalent content as HTML if you’d prefer not to use Markdown:

```html
<a href="https://azure.spring.launcher.com/deploy.html?url=https://github.com/azure/deploy">
    <img src="https://user-images.githubusercontent.com/58474919/236122963-8c0857bb-3822-4485-892a-445fa33f1612.png" alt="Deploy to Azure">
</a>
```

### Button image

When linking to the Azure Button set-up flow, you can either use a raw link or an image link. If using an image, Azure makes available SVG versions at these URLs:

```bash
https://user-images.githubusercontent.com/58474919/236122963-8c0857bb-3822-4485-892a-445fa33f1612.png
```

## Use a custom Git branch

If you’d like the button to deploy from a specific Git branch, you can use a fully qualified GitHub URL as the `branch` parameter:

```bash
https://azure.spring.launcher.com/deploy.html?url=https://github.com/azure/deploy&branch=main
```

## Use a custom module

If you want the button to deploy from a module specified in the source code, you can use the fully qualified GitHub URL as the `module` parameter:

```bash
https://azure.spring.launcher.com/deploy.html?url=https://github.com/azure/deploy&branch=main&module=web
```

## Further reading

For more detailed information about see the following documents:

- [Azure Platform API Reference: App Setups](https://learn.microsoft.com/rest/api/azure/)

## 📑 Keep reading

📓 [Deployment Integrations](https://azure.microsoft.com/solutions/integration-services).

## ⚡ Feedback

◀️ [Log in to submit feedback](https://github.com/).
