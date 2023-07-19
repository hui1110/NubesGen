// getPushResult();

const code = getGithubCode();

const shareProjectButton = document.getElementById("share-project");
const generateProjectButton = document.getElementById("generate-project");

if (generateProjectButton) {
  generateProjectButton.addEventListener("click", event => {
    event.stopPropagation();
    console.log("generate-project");
    generateZipPackage()
  }, false);
}

if (shareProjectButton) {
  const pushToGitHubButton = document.createElement("button");
  pushToGitHubButton.classList.add("button");
  pushToGitHubButton.type = "button";
  pushToGitHubButton.id = "push-to-github";
  pushToGitHubButton.innerHTML = `<span class="button-content" tabindex="-1"><span>Push to GitHub</span></span>`;
  shareProjectButton.parentNode.appendChild(pushToGitHubButton);
  pushToGitHubButton.addEventListener("click", () => {
    console.log("push to github");
    const clientId = "27c83ffc0f8fd2a9859b";
    const githubLoginUrl = "https://github.com/login/oauth/authorize?client_id=";
    location.assign(githubLoginUrl + clientId);
    console.log(code);
  }, false);

}

async function generateZipPackage() {

  let zippedBlob;
  await fetch('https://start.spring.io/starter.zip?' + getMetadataString()).then((
    response) => {
    return response.blob();
  }).then((data) => {
    zippedBlob = new Blob([data], { type: "application/zip" });
    console.log(zippedBlob);
  });;

  const artifactId = document.getElementById('input-artifact').getAttribute('value');
  const zip = new JSZip();
  const downloadzip = await zip.loadAsync(zippedBlob);
  downloadzip.file("azure.txt", 'some content...');

  await fetch('https://raw.githubusercontent.com/hui1110/demo/main/azure.yaml').then((
    response) => {
    return response.blob();
  }).then((data) => {
    downloadzip.file("azure.yaml", data, { binary: true });
  });;

  await fetch('https://raw.githubusercontent.com/hui1110/demo/main/.devcontainer/Dockerfile').then((
    response) => {
    return response.blob();
  }).then((data) => {
    downloadzip.folder('.devcontainer').file("Dockerfile", data, { binary: true });
  });;

  await fetch('https://raw.githubusercontent.com/hui1110/demo/main/.devcontainer/devcontainer.json').then((
    response) => {
    return response.blob();
  }).then((data) => {
    downloadzip.folder('.devcontainer').file("devcontainer.json", data, { binary: true });
  });;

  const finalZipBlob = await downloadzip.generateAsync({ type: "blob" });
  saveAs(finalZipBlob, artifactId + ".zip");

  // const blobUrl = URL.createObjectURL(finalZipBlob);
  // const tempLink = document.createElement('a');
  // const tempLink = document.createElement('a');
  // tempLink.style.display = 'none';
  // tempLink.style.display = 'none';
  // tempLink.href = blobUrl;
  // tempLink.href = blobUrl;
  // tempLink.id = 'link';
  // tempLink.id = 'link';
  // tempLink.setAttribute('download', artifactId);
  // tempLink.setAttribute('download', artifactId);
  // document.body.appendChild(tempLink);
  // console.log(tempLink);
  // tempLink.click();
  // document.body.removeChild(tempLink);

}


const exploreProjectButton = document.getElementById("explore-project");
if (exploreProjectButton) {
  exploreProjectButton.addEventListener("click", async () => {
    console.log("=====");

   
      console.log(localStorage);

      console.log(sessionStorage);

      console.log(location)

      console.log(document);

    // setTimeout(() => {

    //   const selectDiv = document.getElementsByClassName('is-mobile explorer-select');
    //   console.log(selectDiv[0].children[0]);
    //   const option = document.createElement('option');
    //   option.innerText = 'azure.yaml';
    //   option.value = '/demo/azure.yaml';
    //   selectDiv[0].children[0].appendChild(option);
    //   const explorerUl = document.getElementsByClassName('explorer-ul');


    //   const file = '<a href="/#" tabindex="" class="file level-0  "><span class="item-content" tabindex="-1"><span class="text"><span class="icon"><svg aria-hidden="true" focusable="false" data-icon="file" role="img" xmlns="http://www.w3.org/2000/svg" class="icon-file" viewBox="0 0 384 512"><path fill="currentColor" d="M369.9 97.9L286 14C277 5 264.8-.1 252.1-.1H48C21.5 0 0 21.5 0 48v416c0 26.5 21.5 48 48 48h288c26.5 0 48-21.5 48-48V131.9c0-12.7-5.1-25-14.1-34zM332.1 128H256V51.9l76.1 76.1zM48 464V48h160v104c0 13.3 10.7 24 24 24h104v288H48z"></path></svg></span>azure.yaml</span></span></a>';
    //   const li = document.createElement("li");
    //   li.className = "li-file";
    //   li.innerHTML = file;
    //   explorerUl[0].appendChild(li);

    //   const a = '<a href="/#" class="folder level-0"><span class="item-content" tabindex="-1"><span class="text"><svg aria-hidden="true" focusable="false" data-icon="caret-down" role="img" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 320 512" class="icon-caret-down"><path fill="currentColor" d="M31.3 192h257.3c17.8 0 26.7 21.5 14.1 34.1L174.1 354.8c-7.8 7.8-20.5 7.8-28.3 0L17.2 226.1C4.6 213.5 13.5 192 31.3 192z"></path></svg><span class="icon"><svg aria-hidden="true" focusable="false" data-prefix="fas" data-icon="folder" role="img" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512" class="icon-folder"><path fill="currentColor" d="M464 128H272l-64-64H48C21.49 64 0 85.49 0 112v288c0 26.51 21.49 48 48 48h416c26.51 0 48-21.49 48-48V176c0-26.51-21.49-48-48-48z"></path></svg></span>.devcontainer</span></span></a>';
    //   const ul = '<ul class="ul"><li class="li-file"><a href="/#" tabindex="0" class="file level-1 "><span class="item-content" tabindex="0"><span class="text"><span class="icon"><svg aria-hidden="true" focusable="false" data-icon="file" role="img" xmlns="http://www.w3.org/2000/svg" class="icon-file" viewBox="0 0 384 512"><path fill="currentColor" d="M369.9 97.9L286 14C277 5 264.8-.1 252.1-.1H48C21.5 0 0 21.5 0 48v416c0 26.5 21.5 48 48 48h288c26.5 0 48-21.5 48-48V131.9c0-12.7-5.1-25-14.1-34zM332.1 128H256V51.9l76.1 76.1zM48 464V48h160v104c0 13.3 10.7 24 24 24h104v288H48z"></path></svg></span>Dockerfile</span></span></a></li><li class="li-file"><a href="/#" tabindex="" class="file level-1  "><span class="item-content" tabindex="0"><span class="text"><span class="icon"><svg aria-hidden="true" focusable="false" data-icon="file" role="img" xmlns="http://www.w3.org/2000/svg" class="icon-file" viewBox="0 0 384 512"><path fill="currentColor" d="M369.9 97.9L286 14C277 5 264.8-.1 252.1-.1H48C21.5 0 0 21.5 0 48v416c0 26.5 21.5 48 48 48h288c26.5 0 48-21.5 48-48V131.9c0-12.7-5.1-25-14.1-34zM332.1 128H256V51.9l76.1 76.1zM48 464V48h160v104c0 13.3 10.7 24 24 24h104v288H48z"></path></svg></span>devcontainer.json</span></span></a></li></ul>';
    //   const liFolder = document.createElement("li");
    //   liFolder.className = "li-folder folder-hide";
    //   liFolder.innerHTML = a + ul;
    //   liFolder.onclick = function () {
    //     liFolder.className = "li-folder";
    //   };
    //   explorerUl[0].appendChild(liFolder);

    // }, 2000);
  }, false);
}

