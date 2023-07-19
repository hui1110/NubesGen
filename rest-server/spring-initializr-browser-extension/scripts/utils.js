let dependenciesMetadata = [];
let dependenciesNameList = [];

window.onload = async () => {
    const response = await fetch("https://start.spring.io/metadata/config");
    const data = await response.json();
    dependenciesMetadata = data.dependencies.content;
}
// Get push result.
function getPushResult() {
    const query = window.location.search;
    history.replaceState(null, null, location.origin)
    if (query !== "" && query !== null && query !== undefined) {
        const searchParams = new URLSearchParams(query);
        const code = searchParams.get('code')
        if (code === '200' || code === '0') {
            // alert("Push to GitHub succeeded. \n\n Clone address: " + decodeURIComponent(searchParams.get('msg')))
            showResultTip(code, decodeURIComponent(searchParams.get('msg')));
        } else {
            // alert("Push to GitHub failed. \n\n" + decodeURIComponent(searchParams.get('msg')))
            showResultTip(code, decodeURIComponent(searchParams.get('msg')));

        }
    }
}

// Get full URL path
function getFullUrl(type) {
    const clientId = "27c83ffc0f8fd2a9859b";
    const githubLoginUrl = "https://github.com/login/oauth/authorize?client_id=";
    const redirectUri = "http://localhost:8080/login/oauth2/code?";
    const exploreRedirectUri = "http://localhost:8080#!";
    const urlParams = "&baseDir=demo&architecture=none&gitServiceType=github&action=push";
    let url = '';
    if (type === 'push') {
        const encodeRedirectUrl = redirectUri + encodeURIComponent(getMetadataString() + urlParams + '&fromSpringInitializr=push') + '&state=ld2n9rgo&scope=repo workflow codespace';
        url = githubLoginUrl + clientId + '&redirect_uri=' + encodeRedirectUrl;
    } else if (type === 'explore') {
        const projectMetadata = getMetadataString().replace('bootVersion', 'platformVersion').replace('javaVersion', 'jvmVersion');
        url = exploreRedirectUri + projectMetadata + urlParams + '&fromSpringInitializr=explore';
        const params = new URLSearchParams(projectMetadata);
        const javaVersion = params.get('jvmVersion');
        if (javaVersion === '8') {
            url = url.replace(/(jvmVersion=)[^\&]+/, '$1' + '1.8')
        }
    }
    return url;
}

// Get project metadata path string.
function getMetadataString() {

    const groupId = document.getElementById('input-group').getAttribute('value');
    const artifactId = document.getElementById('input-artifact').getAttribute('value');
    const name = document.getElementById('input-name').getAttribute('value');
    const description = document.getElementById('input-description').getAttribute('value');
    const packageName = document.getElementById('input-packageName').getAttribute('value');

    let projectMetadata = getProjectMetadata('springMetadata', fieldTypes, formSpringMetadataRadio) + getProjectMetadata('projectMetadata', fieldTypes, formProjectMetadataRadio) + "groupId=" + groupId + "&artifactId=" + artifactId + "&name=" + name + "&description=" + encodeURIComponent(description) + "&packageName=" + packageName;
    const result = getDependenciesNameList();
    if (result !== undefined) {
        projectMetadata += "&dependencies=" + getDependenciesIdString(result);
    }
    return projectMetadata;
}

//  Convert project metadata object to string
function convertUrlObject(urlObject) {
    let projectUrlQuery = '';
    Object.keys(urlObject).forEach((key) => {
        projectUrlQuery += `${key}=${urlObject[key]}&`
    });
    return projectUrlQuery = `${projectUrlQuery}`;
}

//  Get project metadata. 
function getProjectMetadata(flag, fieldTypes, data) {
    const projectUrlQueryObject = {};
    data.forEach((formItem) => {
        const { type, labelName, outputParamName } = formItem;
        if (type === fieldTypes.radio) {
            let radioGroup;
            if (flag === 'springMetadata') {
                radioGroup = Array.from(document.evaluate(`//label[text()='${labelName}']`, document, null, XPathResult.FIRST_ORDERED_NODE_TYPE, null).singleNodeValue.nextSibling.children[0].children);
            } else {
                radioGroup = Array.from(document.evaluate(`//label[text()='${labelName}']`, document, null, XPathResult.FIRST_ORDERED_NODE_TYPE, null).singleNodeValue.nextSibling.children);
            }
            radioGroup.forEach((radioElement) => {
                if (radioElement.className.indexOf('checked') > -1) {
                    if (radioElement.innerText === 'Gradle - Groovy' && outputParamName === 'type') {
                        projectUrlQueryObject[outputParamName] = 'gradle-project';
                    } else if (radioElement.innerText === 'Gradle - Kotlin' && outputParamName === 'type') {
                        projectUrlQueryObject[outputParamName] = 'gradle-project-kotlin';
                    } else if (radioElement.innerText === 'Maven' && outputParamName === 'type') {
                        projectUrlQueryObject[outputParamName] = 'maven-project';
                    } else if (outputParamName === 'bootVersion') {
                        if (radioElement.innerText.includes('(')) {
                            projectUrlQueryObject[outputParamName] = radioElement.innerText.substring(0, radioElement.innerText.length - 1).replace('(', '-').replace(/\s*/g, "");
                        } else {
                            projectUrlQueryObject[outputParamName] = radioElement.innerText;
                        }
                    } else {
                        projectUrlQueryObject[outputParamName] = radioElement.innerText.toLowerCase();
                    }
                }
            });
        }
    });
    return convertUrlObject(projectUrlQueryObject);
}

//   Get selected dependenices name.
function getDependenciesNameList() {
    const dependenciesElementsList = document.getElementsByClassName('dependencies-list')[0];
    if (dependenciesElementsList === undefined) {
        return dependenciesElementsList;
    }
    Array.from(dependenciesElementsList.children).forEach((item) => {
        const text = item.children[0].children[0];
        let dependencyText = text.innerText;
        if (text.children && text.children.length === 1) {
            const suffixText = text.children[0].innerText;
            let dependencyObject = {
                'name': '',
                'group': ''
            };
            if (suffixText) {
                dependencyObject.group = suffixText.toLowerCase();
                dependencyText = dependencyText.substring(0, dependencyText.indexOf(suffixText))
                dependencyObject.name = dependencyText.toLowerCase();
            }
            dependenciesNameList.push(dependencyObject);
        }
    });
    return dependenciesNameList;
}

//   Get dependencies Id tring by name
function getDependenciesIdString(dependenciesNameList) {
    let dependenciesArray = [];
    let dependenciesIdStringUrl = '';
    if (dependenciesNameList.length !== 0) {
        for (let k = 0; k < dependenciesNameList.length; k++) {
            for (let i = 0; i < dependenciesMetadata.length; i++) {
                if (dependenciesMetadata[i].name.toLowerCase() === dependenciesNameList[k].group) {
                    dependenciesArray = dependenciesMetadata[i].content;
                    for (let j = 0; j < dependenciesArray.length; j++) {
                        if (dependenciesArray[j].name.toLowerCase() === dependenciesNameList[k].name.replace(/(\s*$)/g, "")) {
                            dependenciesIdStringUrl += dependenciesArray[j].id + ',';
                        }
                    }
                }
            }
        }
        return dependenciesIdStringUrl.substring(0, dependenciesIdStringUrl.lastIndexOf(','));
    }
}


function showResultTip(code, message) {

    const container = document.createElement("div");
    const title = document.createElement("div");
    const btnClose = document.createElement('button');
    const buttonCss = 'color: #000;font-size: 12px;font-weight: 600;margin-right: 1rem; background: #fff;font-family: sans-serif;padding: 0.4rem 1.0rem 0.4rem;';

    if (code === '200' || code === '0') {
        container.style.cssText = 'display: block;position: absolute;top: 10%; left:35%;width:392px;background-color: white;box-shadow: 0 0 0 3px rgba(220,232,232,.3);border-left: 6px solid #222;padding: 12px;border-left-color: #6db33f;';
        title.style.cssText = `color: green;margin: 0 0 10px 0;font-size: 16px;font-weight: bold;display: block;font-family: sans-serif;`;
        title.innerText = 'Your App is ready!';

        const cloned = document.createElement('div');
        cloned.style.cssText = `line-height: 14px;font-weight: normal;display: block;font-family: sans-serif;font-size: 14px;`;
        cloned.innerText = 'Your application is now on GitHub ready to be cloned:';

        const code = document.createElement('code');
        code.style.cssText = 'font-family: monospace,monospace;font-size: 14px;display: block;margin: 15px 0 15px 0;padding: 5px 10px;background-color: rgba(100,100,100,.2);';
        code.innerText = 'git clone ' + message;

        const btnCopy = document.createElement('button');
        btnCopy.style.cssText = buttonCss;
        btnCopy.innerText = 'COPY';
        btnCopy.onmouseover = function () {
            btnCopy.style.color = '#FFFFFF';
            btnCopy.style.backgroundColor = '#000000';
        };
        btnCopy.onmouseout = function () {
            btnCopy.style.color = '#000000';
            btnCopy.style.backgroundColor = '#FFFFFF';
        };
        btnCopy.addEventListener("click", () => {
            navigator.clipboard.writeText(code.innerText).then(() => {
            })
            btnCopy.innerText = 'COPIED';
            setTimeout(function () {
                btnCopy.innerText = 'COPY';
            }, 3000);
        }, false);

        const btnShowrepo = document.createElement('button');
        btnShowrepo.style.cssText = buttonCss;
        btnShowrepo.innerText = 'SHOW REPO';
        btnShowrepo.onmouseover = function () {
            btnShowrepo.style.color = '#FFFFFF';
            btnShowrepo.style.backgroundColor = '#000000';
        };
        btnShowrepo.onmouseout = function () {
            btnShowrepo.style.color = '#000000';
            btnShowrepo.style.backgroundColor = '#FFFFFF';
        };
        btnShowrepo.addEventListener("click", () => {
            location.assign(message);
        }, false);

        btnClose.style.cssText = buttonCss;

        container.appendChild(title);
        container.appendChild(cloned);
        container.appendChild(code);
        container.appendChild(btnCopy);
        container.appendChild(btnShowrepo);

    } else {
        container.style.cssText = 'display: block;position: absolute;top: 10%; left:35%;width:392px;background-color: white;box-shadow: 0 0 0 3px rgba(220,232,232,.3);border-left: 6px solid #222;padding: 12px;border-left-color: #e74c3c;';
        title.style.cssText = `display: block;margin: 0 0 10px 0;color: red;font-size: 16px;font-weight: bold;font-family: sans-serif;`;
        title.innerText = 'Some mistake happend!';

        const msg = document.createElement('div');
        msg.style.cssText = `line-height: 14px;font-weight: normal;display: block;font-family: sans-serif;font-size: 14px;`;
        msg.innerHTML = '<p style="line-height:20px;">' + message + '</p>';

        btnClose.style.cssText = 'color: #000;float:right;font-size: 12px;font-weight: 600;margin-right: 1rem; background: #fff;font-family: sans-serif;padding: 0.4rem 1.0rem 0.4rem;';

        container.appendChild(title);
        container.appendChild(msg);

    }

    btnClose.innerText = 'CLOSE';
    btnClose.onmouseover = function () {
        btnClose.style.color = '#FFFFFF';
        btnClose.style.backgroundColor = '#000000';
    };
    btnClose.onmouseout = function () {
        btnClose.style.color = '#000000';
        btnClose.style.backgroundColor = '#FFFFFF';
    };
    btnClose.addEventListener("click", () => {
        document.body.removeChild(container);
    }, false);

    container.appendChild(btnClose);
    document.body.appendChild(container);
}

// function loadScript() {
//     const script = document.createElement('script');
//     script.type = 'text/javascript';
//     script.src = 'https://unpkg.com/jszip@3.7.1/dist/jszip.js';
//     document.head.appendChild(script);
// }



function getGithubCode() {
    const query = location.search;
    history.replaceState(null, null, location.origin)
    let code;
    if (query !== "" && query !== null && query !== undefined) {
        const searchParams = new URLSearchParams(query);
        code = searchParams.get('code')
    }
    return code;
}