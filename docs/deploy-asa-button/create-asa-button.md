# Creating a Deploy to Azure Button

__Table of Contents:__

[[toc]]

The *Deploy to Azure* button enables users to deploy apps to Azure without leaving the web browser, and with little or no configuration. The button is ideal for customers, open-source project maintainers or add-on providers who wish to provide their customers with a quick and easy way to deploy and configure a Azure Spring Apps.

The button is well-suited for use in README files, and is intended to serve as a replacement to a list of manual steps typically required to configure an app.

Here’s an example button that deploys a sample to Azure Spring Apps:

[![Deploy to Azure](https://raw.githubusercontent.com/Azure/azure-quickstart-templates/master/1-CONTRIBUTION-GUIDE/images/deploytoazure.svg?sanitize=true)]()

This document describes the requirements for apps that use the ‘Deploy to Azure’ service, and how to use these buttons make it easy to deploy source code you maintain to Azure Spring Apps.

## Requirements

The basic requirements for creating a button are that your app has a valid app.json file in its root directory, and that the app source code is hosted in a GitHub repository.

:::tip
Azure Button also works with repositories that have submodules. Azure Button gets the source code from GitHub and generates a tar.gz file. You can choose the submodule name to deploy.
:::

## Button Terms of Use

The Azure Terms of Use (Default) governs the Terms of Use of your button unless you provide your own Terms of Use in your GitHub repository. It is common practice to link to your Terms of Use in your README file or to add them as a license file to your GitHub repository.

## Adding the Azure button

Here’s an example:

```markdown
[![Deploy to Azure](https://raw.githubusercontent.com/Azure/azure-quickstart-templates/master/1-CONTRIBUTION-GUIDE/images/deploytoazure.svg?sanitize=true)](https://azure.com/deploy?url=https://github.com/azure/deploy)
```

Here’s the equivalent content as HTML if you’d prefer not to use Markdown:

```html
<a href="https://azure.com/deploy?url=https://github.com/azure/deploy">
  <img src="https://raw.githubusercontent.com/Azure/azure-quickstart-templates/master/1-CONTRIBUTION-GUIDE/images/deploytoazure.svg?sanitize=true" alt="Deploy to Azure">
</a>
```

A `button.svg` is also available.

### Adding an explicit parameter

Use the following Markdown snippet as a template, changing the url query parameter to the URL of your repository:

```markdown
[![Deploy to Azure](https://raw.githubusercontent.com/Azure/azure-quickstart-templates/master/1-CONTRIBUTION-GUIDE/images/deploytoazure.svg?sanitize=true)](https://azure.com/deploy?url=https://github.com/azure/deploy)
```

Here’s the equivalent content as HTML if you’d prefer not to use Markdown:

```html
<a href="https://azure.com/deploy?url=https://github.com/azure/deploy">
  <img src="https://www.herokucdn.com/deploy/button.svg" alt="Deploy to Azure">
</a>
```

### Parametrizing buttons

It’s possible to supply URL parameters in the Button.

For example, you might have the following button URL:

```bash
https://azure.com/deploy?url=https://github.com/azure/deploy
```

### Button image

When linking to the Azure Button set-up flow, you can either use a raw link or an image link. If using an image, Azure makes available both PNG and SVG versions at these URLs:

```bash
https://raw.githubusercontent.com/Azure/azure-quickstart-templates/master/1-CONTRIBUTION-GUIDE/images/deploytoazure.svg?sanitize=true
```

## Using a custom Git branch

If you’d like the button to deploy from a specific Git branch, you can use a fully qualified GitHub URL as the branch parameter:

```bash
https://azure.com/deploy?url=https://github.com/azure/deploy&branch=mian
```

## Further reading

For more detailed information about see the following documents:

Azure Platform API Reference: App Setups

## Keep reading

Deployment Integrations

## Feedback

Log in to submit feedback.
