let startTime = Date.now();
let totalActiveTime = 0;
let lastActiveTime = startTime;
let isPageVisible = true;
let currentQuery = "";
let userId = "";
let childId = "";
let analysisData = {
  isHarmful: false,
  predictedResult: '',
  sentimentScore: 0
}; 
let updateInterval = null;
let isTracking = false;
let isHarmful = false;
let notificationSent = false;
let lastUpdateTime = 0;
const UPDATE_INTERVAL = 1000;
const HARM_DETECTION_THRESHOLD = 10000;

console.log("Content script initialized at:", new Date().toLocaleTimeString());

const sendTimeData = async (timeSpent, reason = "unknown") => {
  if (!currentQuery || !userId || timeSpent <= 0) {
    console.log(`Skipping time update - query: "${currentQuery}", userId: "${userId}", timeSpent: ${timeSpent}`);
    return false;
  }

  const payload = {
    userId,
    childId,
    query: currentQuery,
    timeSpent: timeSpent,
    reason: reason,
    isHarmful: analysisData.isHarmful,
    predictedResult: analysisData.predictedResult,
    sentimentScore: analysisData.sentimentScore,
    analysisData: analysisData 
  };
  
  console.log(` Sending complete data with reason: ${reason}`);
  console.log(" Full payload:", JSON.stringify(payload, null, 2));
try {
    const response = await new Promise((resolve, reject) => {
      chrome.runtime.sendMessage({
        type: "logTimeSpent",
        data: payload,
        includeAnalysisData: true 
      }, (response) => {
        if (chrome.runtime.lastError) {
          reject(chrome.runtime.lastError);
        } else {
          resolve(response);
        }
      });
    });

    console.log(" Background script response:", response);
    return true;
  } catch (error) {
    console.error("Message passing failed:", error);

    try {
      const response = await fetch("http://localhost:5000/api/users/logTimeSpent", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload),
        keepalive: true
      });
      
      if (response.ok) {
        console.log(" Direct fetch successful:", response.status);
        return true;
      } else {
        console.error(" Direct fetch failed:", response.status);
        return false;
      }
    } catch (fetchError) {
      console.error(" All methods failed:", fetchError);
      return false;
    }
  }
};

const analyzeQuery = async (query) => {
  console.log("Analyzing query:", query);
  try {
    const response = await fetch("http://localhost:5000/api/users/analyze-query", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ query, userId, childId })
    });
    
    if (response.ok) {
      const data = await response.json();
      console.log("Analysis result:", data);
      
      analysisData = {
        isHarmful: data.isHarmful || false,
        predictedResult: data.predictedResult || '',
        sentimentScore: data.sentimentScore || 0
      };
      
      isHarmful = analysisData.isHarmful;
      lastUpdateTime = Date.now();
      return isHarmful;
    }
    return false;
  } catch (error) {
    console.error("Error analyzing query:", error);
    return false;
  }
};

const calculateActiveTime = () => {
  if (isPageVisible && isTracking) {
    const now = Date.now();
    totalActiveTime += now - lastActiveTime;
    lastActiveTime = now;
  }
  return Math.floor(totalActiveTime / 1000);
};

const startSearchTracking = () => {
  if (isTracking) return;
  
  console.log("Starting time tracking for search");
  isTracking = true;
  startTime = Date.now();
  lastActiveTime = startTime;
  totalActiveTime = 0;
  isPageVisible = true;
  
  let lastUpdateTime = Date.now();
  let lastSentTime = 0;
  
  if (updateInterval) clearInterval(updateInterval);
  updateInterval = setInterval(async () => {
    if (isPageVisible && isTracking) {
      const now = Date.now();
      const currentTimeSpent = calculateActiveTime();
      const timeSinceLastUpdate = now - lastUpdateTime;
      
      if (timeSinceLastUpdate >= 1000) {
        lastUpdateTime = now;
        
        if (isHarmful && !notificationSent && currentTimeSpent >= 10) {
          notificationSent = true;
          lastSentTime = currentTimeSpent;
          try {
            await sendTimeData(10, 'harmful-query');
            console.log('Immediate notification sent at 10s mark');
          } catch (error) {
            console.error('Failed to send notification:', error);
            notificationSent = false;
          }
        }
        
        if (isHarmful && currentTimeSpent % 5 === 0) {
          console.log(` Harmful query tracking: ${currentTimeSpent}s spent`);
        }
      }
      
      totalActiveTime += now - lastActiveTime;
      lastActiveTime = now;
    }
  }, 100);
};
document.addEventListener('visibilitychange', () => {
  console.log("Page visibility changed:", document.hidden ? "HIDDEN" : "VISIBLE");
  if (document.hidden) {
    if (isPageVisible) {
      const sessionTime = Date.now() - lastActiveTime;
      totalActiveTime += sessionTime;
      console.log(`Tab hidden. Session: ${Math.floor(sessionTime/1000)}s, Total: ${Math.floor(totalActiveTime/1000)}s`);
      isPageVisible = false;
    }
  } else {
    if (!isPageVisible) {
      lastActiveTime = Date.now();
      isPageVisible = true;
      console.log("Resuming time tracking");
    }
  }
});

const trackUserInteraction = (event) => {
  if (isTracking) {
    lastActiveTime = Date.now();
  }
};

['click', 'scroll', 'keydown', 'mousemove', 'touchstart'].forEach(event => {
  document.addEventListener(event, trackUserInteraction, { passive: true, once: false });
});

(async () => {
  try {
    console.log("Starting authentication check...");
    
    const [syncData, localData] = await Promise.all([
      new Promise(resolve => chrome.storage.sync.get(['userId', 'selectedChild'], resolve)),
      new Promise(resolve => chrome.storage.local.get(['userId', 'selectedChild'], resolve))
    ]);

    userId = syncData.userId || localData.userId;
    const selectedChild = syncData.selectedChild || localData.selectedChild;

    console.log("Auth check result:", { 
      hasUserId: !!userId, 
      hasSelectedChild: !!selectedChild,
      childName: selectedChild?.name 
    });

    if (!userId) {
      console.log("User not logged in - opening login popup");
      chrome.runtime.sendMessage({ type: "openLoginPopup" });
      return;
    }

    if (!selectedChild) {
      console.log("No child profile selected - opening profile selection");
      chrome.runtime.sendMessage({ type: "openProfileSelection" });
      return;
    }

    childId = selectedChild._id;

    let searchQuery = document.title || "";
    if (searchQuery.endsWith(" - Google Search")) {
      searchQuery = searchQuery.replace(" - Google Search", "").trim();
    }

    if (!searchQuery) {
      console.log("Empty or invalid query title");
      return;
    }

    currentQuery = searchQuery;
    console.log(`Analyzing query for ${selectedChild.name}: "${searchQuery}"`);
    
    startSearchTracking();
    console.log(`Time tracking started for query: "${searchQuery}"`);
    
    try {
      const res = await fetch("http://localhost:5000/api/users/analyzeQuery", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ 
          userId,
          childId: selectedChild._id,
          childName: selectedChild.name,
          query: searchQuery 
        }),
      });
      
      if (res.ok) {
        const data = await res.json();
        console.log('Analysis response:', data);
        
        analysisData = {
          isHarmful: data.isHarmful || false,
          predictedResult: data.predictedResult || '',
          sentimentScore: data.sentimentScore || 0
        };
        
        isHarmful = analysisData.isHarmful;
        console.log(`Query marked as ${isHarmful ? 'HARMFUL' : 'SAFE'}`);
        console.log("Analysis data:", analysisData);
      }
    } catch (error) {
      console.error("Error analyzing query:", error);
    }

    notificationSent = false;
    startTime = Date.now();
    totalActiveTime = 0;
    lastActiveTime = startTime;
    
    let hasSentData = false;
    const handlePageExit = async () => {
      if (!hasSentData && isTracking) {
        const finalTime = calculateActiveTime();
        console.log(`Page exit - saving final record: ${finalTime}s spent on "${currentQuery}"`);
        hasSentData = true;
        
        await sendTimeData(finalTime, 'tab_closed');
      } else if (!isTracking) {
        console.log(' Page exit - no active tracking, skipping save');
      }
    };

    window.addEventListener("beforeunload", handlePageExit);
    window.addEventListener("pagehide", handlePageExit);
    window.addEventListener("unload", handlePageExit);

    console.log(" Content script setup complete");

  } catch (error) {
    console.error(" Content script error:", error);
  }
})();