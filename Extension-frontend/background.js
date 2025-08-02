let activeTabId;
let pendingData = {}; 

chrome.tabs.onRemoved.addListener((tabId) => {
  if (pendingData[tabId]) {
    console.log(`Tab ${tabId} closed - saving pending data`);
    saveData(pendingData[tabId]);
    delete pendingData[tabId];
  }
});

chrome.tabs.onUpdated.addListener((tabId, changeInfo, tab) => {
  if (changeInfo.url && pendingData[tabId]) {
    const isSearchPage = changeInfo.url.includes('google.com/search') || 
                         changeInfo.url.includes('yahoo.com/search');
    
    if (isSearchPage) {
      console.log(`New search detected in tab ${tabId} - saving previous data`);
      saveData(pendingData[tabId]);
      delete pendingData[tabId];
    }
  }
});

function saveData(data) {
  const dataToSend = {
    userId: data.userId,
    childId: data.childId,
    query: data.query,
    timeSpent: data.timeSpent,
    reason: data.reason || 'tab_closed',
    isHarmful: data.isHarmful || false,
    predictedResult: data.predictedResult || 
                   (data.analysisData?.predictedResult || 'unknown'),
    sentimentScore: data.sentimentScore || 
                   (data.analysisData?.sentimentScore || 0),
    analysisData: {
      isHarmful: data.isHarmful || false,
      predictedResult: data.predictedResult || 
                     (data.analysisData?.predictedResult || 'unknown'),
      sentimentScore: data.sentimentScore || 
                     (data.analysisData?.sentimentScore || 0)
    }
  };

  console.log("Saving complete data to server:", JSON.stringify(dataToSend, null, 2));
  
  fetch("http://localhost:5000/api/users/logTimeSpent", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(dataToSend)
  })
  .then(response => response.json())
  .then(data => console.log("Data saved successfully:", data))
  .catch(error => console.error(" Data save failed:", error));
}

chrome.action.onClicked.addListener((tab) => {
  console.log("Extension icon clicked");
  checkAuthAndRedirect();
});

async function checkAuthAndRedirect() {
  try {
    const [syncData, localData] = await Promise.all([
      new Promise(resolve => chrome.storage.sync.get(['userId', 'selectedChild'], resolve)),
      new Promise(resolve => chrome.storage.local.get(['userId', 'selectedChild'], resolve))
    ]);

    const userId = syncData.userId || localData.userId;
    const selectedChild = syncData.selectedChild || localData.selectedChild;

    if (!userId) {
      chrome.tabs.create({ url: chrome.runtime.getURL("popup.html") });
      return;
    }

    if (!selectedChild) {
      chrome.tabs.create({ url: chrome.runtime.getURL("child-profile.html") });
      return;
    }

    console.log("User is authenticated and profile selected");
  } catch (error) {
    console.error("Auth check error:", error);
    chrome.tabs.create({ url: chrome.runtime.getURL("popup.html") });
  }
}

chrome.runtime.onMessage.addListener((request, sender, sendResponse) => {
  console.log("Message received:", request.type || request.action);
  
  if (request.type === "logTimeSpent") {
    const tabId = sender.tab?.id;
    
    const completeData = {
      userId: request.data.userId,
      childId: request.data.childId,
      query: request.data.query,
      timeSpent: request.data.timeSpent,
      reason: request.data.reason || 'unknown',
      isHarmful: request.data.isHarmful || false,
      predictedResult: request.data.predictedResult || 
                     (request.data.analysisData?.predictedResult || 'unknown'),
      sentimentScore: request.data.sentimentScore || 
                     (request.data.analysisData?.sentimentScore || 0),
      analysisData: request.data.analysisData || {
        isHarmful: request.data.isHarmful || false,
        predictedResult: request.data.predictedResult || 'unknown',
        sentimentScore: request.data.sentimentScore || 0
      }
    };

    console.log("📦 Processed message data:", JSON.stringify(completeData, null, 2));

    if (request.data.reason === 'harmful-query') {
      console.log('Immediately saving harmful query data');
      saveData(completeData);
      sendResponse({ success: true, status: "saved" });
      return true;
    }
    
    if (tabId) {
      pendingData[tabId] = completeData;
      console.log(`Data queued for tab ${tabId}`);
      sendResponse({ success: true, status: "queued" });
      return true;
    }
    
    sendResponse({ success: true, status: "no_tab_context" });
    return true;
  }
  if (request.type === "openProfileSelection") {
    chrome.tabs.create({ url: chrome.runtime.getURL("child-profile.html") });
    sendResponse({ status: "profileSelectionOpened" });
    return true;
  }
  if (request.type === "setUserId") {
    chrome.storage.local.set({ userId: request.userId }, () => {
      console.log("User ID saved:", request.userId);
      sendResponse({ status: "success" });
    });
    return true;
  }
  if (request.type === "getUserId") {
    chrome.storage.local.get("userId", (result) => {
      sendResponse({ userId: result.userId || null });
    });
    return true;
  }
  if (request.action === "childProfileSelected") {
    chrome.storage.sync.set({ selectedChild: request.childData });
    chrome.storage.local.set({ selectedChild: request.childData });
    sendResponse({ status: "profileSaved" });
    return true;
  }
  if (request.type === "checkAuth") {
    chrome.storage.sync.get(['userId', 'selectedChild'], (syncData) => {
      if (!syncData.userId) {
        chrome.storage.local.get(['userId', 'selectedChild'], (localData) => {
          sendResponse(localData.userId ? 
            { loggedIn: true, source: 'local' } : 
            { loggedIn: false });
        });
      } else {
        sendResponse({ loggedIn: true, source: 'sync' });
      }
    });
    return true;
  }
});

chrome.tabs.onActivated.addListener(activeInfo => {
  activeTabId = activeInfo.tabId;
  console.log(`Active tab changed to ID: ${activeTabId}`);
});

function storeActiveTab() {
  if (activeTabId) {
    chrome.storage.local.set({ activeTabId: activeTabId });
  }
}

chrome.runtime.onInstalled.addListener((details) => {
  console.log("SafeMind Watch extension installed");
  
  if (details.reason === 'install') {
    console.log("First time install - checking authentication");
    setTimeout(() => {
      checkAuthAndRedirect();
    }, 1000);
  }
});
setInterval(() => {
  console.log("Checking for stale pending data...");
  const now = Date.now();
  for (const tabId in pendingData) {
    if (pendingData[tabId].timestamp && now - pendingData[tabId].timestamp > 300000) { 
      console.log(`Cleaning up stale data for tab ${tabId}`);
      saveData(pendingData[tabId]);
      delete pendingData[tabId];
    }
  }
}, 60000); 