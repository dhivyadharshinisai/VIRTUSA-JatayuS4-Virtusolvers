document.addEventListener("DOMContentLoaded", async () => {
  const profileGrid = document.getElementById('profileGrid');
  const colors = ['#e50914', '#f5a623', '#1e90ff', '#6a1b9a', '#43a047']; 

  try {
    const { user: userData } = await new Promise(resolve => {
      chrome.storage.sync.get(['user'], (syncData) => {
        if (syncData.user) {
          resolve(syncData);
        } else {
          chrome.storage.local.get(['user'], (localData) => {
            resolve(localData);
          });
        }
      });
    });

    if (!userData || !userData.children || userData.children.length === 0) {
      console.error("No user data or child profiles found");
      window.location.href = "popup.html";
      return;
    }

    userData.children.forEach((child, index) => {
      const card = document.createElement('div');
      card.className = 'profile-card';

      const avatar = document.createElement('div');
      avatar.className = 'profile-avatar-wrapper';
      avatar.style.backgroundColor = colors[index % colors.length];
      avatar.textContent = child.name.charAt(0).toUpperCase();

      const namePara = document.createElement('div');
      namePara.className = 'profile-name';
      namePara.textContent = child.name;

      card.appendChild(avatar);
      card.appendChild(namePara);

      card.addEventListener('click', async () => {
        const profileData = {
          ...child,
          parentEmail: userData.email,
          parentName: userData.name,
          parentPhone: userData.phone,
          selectedAt: new Date().toISOString()
        };

        await new Promise(resolve => {
          chrome.storage.sync.set({ selectedChild: profileData }, () => {
            chrome.storage.local.set({ selectedChild: profileData }, resolve);
          });
        });

        window.close();
        chrome.runtime.sendMessage({ action: "childProfileSelected", childData: profileData }, (response) => {
          if (chrome.runtime.lastError) {
            console.error("Message failed:", chrome.runtime.lastError);
          }
        });
      });

      profileGrid.appendChild(card);
    });

  } catch (error) {
    console.error("Profile selection error:", error);
    window.location.href = "popup.html";
  }
});
