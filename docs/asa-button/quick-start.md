# Create a Deploy to Azure Spring Apps Button

__Table of Contents:__

[[toc]]

The `Deploy to Azure Spring Apps` button enables users to deploy applications to Azure Spring Apps without leaving the web browser and requires only simple configuration.

The basic requirement of the creation button is that your application source code hosting is in the GitHub repository. We will add deploy button to the `README.md` file.

Here’s an example button that deploys a sample to Azure Spring Apps:

[![Deploy to Azure Spring Apps](https://user-images.githubusercontent.com/58474919/236122963-8c0857bb-3822-4485-892a-445fa33f1612.png)]()

This document describes how to easily deploy your maintained source code to Azure Spring Apps using the `Deploy to Azure Spring Apps` button.

## Requirements

- The application source code is hosted in the GitHub public repository.
- An Azure AD administrative roles user.

## Set up account

If you use an administrator account, you can start using the ASA buttons after logging in. If your account does not have an administrator role, see [Assign user roles with Azure Active Directory](https://learn.microsoft.com/azure/active-directory/fundamentals/active-directory-users-assign-role-azure-portal) to ensure that your account has an administrator role.

## Add deploy to Azure Spring Apps Button

The following is an example that changes the template query parameter to the `url` of the repository:

:::tip
When adding only the `url`, Azure Button will pull the source code from the default branch of your GitHub repository.
:::

**Markdown**:

```markdown
[![Deploy to Azure](https://user-images.githubusercontent.com/58474919/236122963-8c0857bb-3822-4485-892a-445fa33f1612.png)](https://azure.spring.launcher.com/deploy.html?url=https://github.com/azure/deploy)
```

Here’s the equivalent content as HTML if you’d prefer not to use Markdown:

```html
<a href="https://azure.spring.launcher.com/deploy.html?url=https://github.com/azure/deploy">
    <img src="https://user-images.githubusercontent.com/58474919/236122963-8c0857bb-3822-4485-892a-445fa33f1612.png" alt="Deploy to Azure Spring Apps">
</a>
```

**AsciiDoc**:
    
```asciidoc
image:https://user-images.githubusercontent.com/58474919/236122963-8c0857bb-3822-4485-892a-445fa33f1612.png[link="https://azure.spring.launcher.com/deploy.html?url=https://github.com/azure/deploy" alt="Deploy to Azure Spring Apps" width="200px"]
```

### Button image

When linking to the Azure Button set-up flow, you can either use a raw link or an image link. If using an image, Azure makes available SVG versions at these URLs:

```url
https://user-images.githubusercontent.com/58474919/236122963-8c0857bb-3822-4485-892a-445fa33f1612.png
```

## Use a custom Git branch

If you’d like the button to deploy from a specific Git branch, you can use a fully qualified GitHub URL as the `branch` parameter:

```url
https://azure.spring.launcher.com/deploy.html?url=https://github.com/azure/deploy&branch=main
```

## Use a custom module

If you want the button to deploy from a module specified in the source code, you can use the fully qualified GitHub URL as the `module` parameter:

```url
https://azure.spring.launcher.com/deploy.html?url=https://github.com/azure/deploy&branch=main&module=web
```

## Further reading

For more detailed information about see the following documents:

- [Azure Platform API Reference: App Setups](https://learn.microsoft.com/rest/api/azure/)

## 📑 Keep reading

📓 [Deployment Integrations](https://azure.microsoft.com/solutions/integration-services).

## ⚡ Feedback

◀️ [Log in to submit feedback](https://github.com/).
