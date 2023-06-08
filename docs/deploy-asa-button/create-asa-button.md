# Create a Deploy to Azure Spring Apps Button

__Table of Contents:__

[[toc]]

The `Deploy to Azure Spring Apps` button enables users to deploy applicatioin to Azure Spring Apps without leaving the web browser and with little or no configuration. The button is ideal for customers, open-source project maintainers, or add-on providers who wish to provide their customers with a quick and easy way to deploy application to Azure Spring Apps.

The basic requirement of the creation button is that your application source code hosting is in the GitHub repository. We will add deploy button to the `README.md` file.

Here’s an example button that deploys a sample to Azure Spring Apps:

[![Deploy to Azure Spring Apps](https://user-images.githubusercontent.com/58474919/236122963-8c0857bb-3822-4485-892a-445fa33f1612.png)]()

This document describes how to easily deploy your maintained source code to Azure Spring Apps using the `Deploy to Azure Spring Apps` button.

## Requirements

- The application source code is hosted in the GitHub public repository.
- A service principal.
- An Azure AD admin user.

## Create a service principal

See this [article](https://learn.microsoft.com/en-us/azure/active-directory/develop/howto-create-service-principal-portal#register-an-application-with-azure-ad-and-create-a-service-principal) to register an application with Azure AD and create a service principal, then [create a new application secret](https://learn.microsoft.com/en-us/azure/active-directory/develop/howto-create-service-principal-portal#option-3-create-a-new-application-secret).
ASA button needs to be authenticated and authorized by the service principal, use Azure resource manager to create an Azure Spring Apps app that deploys the source code. 

## Set up account

If you use an administrator account, you can start using the ASA button after logging in. If you do not want to use an administrator account, you can create a new account and grant access to the service principal to use the ASA button by following the steps below.

:::tip
The following steps need to be in the same tenant.
:::

1. See this [article](https://learn.microsoft.com/en-us/azure/active-directory/fundamentals/add-users-azure-active-directory#add-a-new-user) to create a new user.
2. Grant permission.

    - Select **Delegated permission** in **API permissions** of the service principal and add the following permissions:
      - openid
      - profile
      - offline_access

    - Select **Azure Service Management** in **API permissions** of the service principal and add the following permissions:
      - user_impersonation
     
3. [Grant admin consent in App registrations](https://learn.microsoft.com/en-us/azure/active-directory/manage-apps/grant-admin-consent?pivots=portal#grant-admin-consent-in-app-registrations).
4. [Assign a user as an administrator of an Azure subscription](https://docs.microsoft.com/en-us/azure/role-based-access-control/role-assignments-portal#assign-a-user-as-an-administrator-of-an-azure-subscription).

## Button Terms of Use

The Azure Terms of Use (Default) governs the Terms of Use of your button unless you provide your own Terms of Use in your GitHub repository. It is common practice to link to your Terms of Use in your README file or to add them as a license file to your GitHub repository.

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
