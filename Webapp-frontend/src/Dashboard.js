import React, { useEffect, useState } from 'react';
import { useLocation } from 'react-router-dom';
import { Pie, Bar } from 'react-chartjs-2';
import { jsPDF } from "jspdf";
import {
  Chart as ChartJS,
  ArcElement,
  Tooltip,
  Legend,
  LineElement,
  PointElement,
  CategoryScale,
  LinearScale,
  BarElement,
} from 'chart.js';
import ChartDataLabels from 'chartjs-plugin-datalabels';
import HealthCareComponent from './HealthCareProvider.js';
import logo from './assets/logoimg.jpeg';
import './Styles/Dashboard.css';
import { useNavigate } from 'react-router-dom';

ChartJS.register(
  ArcElement,
  Tooltip,
  Legend,
  ChartDataLabels,
  LineElement,
  PointElement,
  CategoryScale,
  LinearScale,
  BarElement
);

const QUERY_TYPE_LABELS = ['Anxiety', 'Depression', 'Isolation', 'Suicide', 'No Risk'];
const QUERY_TYPE_COLORS = ['#F4A950','#ADD8E6', '#9AA6B2','#E52020','#1DCD9F',];
const STORAGE_KEY = 'selectedChildProfile';

const parseCustomDate = (dateString) => {
  if (!dateString) return null;
  if (dateString.includes('T')) {
    const date = new Date(dateString);
    if (!isNaN(date.getTime())) return date;
  }
  if (dateString.includes('-') && dateString.includes(',')) {
    try {
      const [datePart, timePart] = dateString.split(', ');
      const [year, month, day] = datePart.split('-').map(Number);
      const [time, period] = timePart.split(' ');
      const [hours, minutes, seconds] = time.split(':').map(Number);
      let hour24 = hours;
      if (period === 'pm' && hours < 12) hour24 += 12;
      if (period === 'am' && hours === 12) hour24 = 0;
      const date = new Date(year, month - 1, day, hour24, minutes, seconds);
      if (!isNaN(date.getTime())) return date;
    } catch (e) {
      return null;
    }
  }
  const date = new Date(dateString);
  return !isNaN(date.getTime()) ? date : null;
};

const saveSelectedChildToStorage = (child) => {
  if (child) {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(child));
  } else {
    localStorage.removeItem(STORAGE_KEY);
  }
};

const getSelectedChildFromStorage = () => {
  const stored = localStorage.getItem(STORAGE_KEY);
  return stored ? JSON.parse(stored) : null;
};

const Dashboard = ({ userDetails, onLogout }) => {
  const location = useLocation();
  const { parentUser, selectedChild: initialSelectedChild } = location.state || {};
  const [selectedChild, setSelectedChild] = useState(() => {
    return initialSelectedChild || getSelectedChildFromStorage();
  });
  const [searchHistories, setSearchHistories] = useState([]);
  const [loading, setLoading] = useState(true);
  const [filter, setFilter] = useState('last_week');
  const [currentPage, setCurrentPage] = useState('dashboard');
  const [selectedGraph, setSelectedGraph] = useState('harmful');
  const [queryTypeCounts, setQueryTypeCounts] = useState([0, 0, 0, 0, 0]);
  const [showProfileSelector, setShowProfileSelector] = useState(false);
  const effectiveUser = parentUser || userDetails;
  const [exportRange, setExportRange] = useState({start: '',end: '',showDialog: false});

  const getLastWeekRange = () => {
    const end = new Date();
    end.setDate(end.getDate() - 1); 
    end.setHours(23, 59, 59, 999, 0);
    const start = new Date(end);
    start.setDate(end.getDate() - 6); 
    start.setHours(0, 0, 0, 0);
    return {
      start: formatDateForInput(start),
      end: formatDateForInput(end)
    };
  };

  const formatDateForInput = (date) => {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  };

  const [customRange, setCustomRange] = useState(getLastWeekRange());
  const [specificDate, setSpecificDate] = useState(() => {
    const today = new Date();
    return today.toISOString().split('T')[0];
  });

  const navigate = useNavigate();
  const [harmfulNonHarmfulRatio, setHarmfulNonHarmfulRatio] = useState({ harmful: 0, nonHarmful: 0 });
  const [topRiskCategories, setTopRiskCategories] = useState([]);
  const [riskPeaksByHour, setRiskPeaksByHour] = useState([]);
  const [riskPeaksByDay, setRiskPeaksByDay] = useState([]);
  const [predictionScores, setPredictionScores] = useState([]);
  const [exposureTimeData, setExposureTimeData] = useState([]);
  const [latestPrediction, setLatestPrediction] = useState(null);

  const graphNavigation = [
    { id: 'harmful', name: 'Query Analysis', icon: 'fa-chart-pie' },
    { id: 'hourly-peaks', name: 'Risk Peaks by Hour', icon: 'fa-clock' },
    { id: 'daily-peaks', name: 'Risk Peaks by Day', icon: 'fa-calendar-day' },
    { id: 'query-types', name: 'Mind Analysis', icon: 'fa-pie-chart' },
    { id: 'overall-risk', name: 'Sentimental Score', icon: 'fa-heartbeat' },
    { id: 'exposure-time', name: 'Exposure Time', icon: 'fa-hourglass-half' },
    { id: 'allSearch', name: 'Abnormal Queries', icon: 'fa-database' },
    { id: 'sos-reports', name: 'SOS Reports', icon: 'fa-exclamation-triangle' },
    { id: 'healthcare', name: 'Specialist', icon: 'fa-user-md' }
  ];

  const getGraphHeading = () => {
    switch (selectedGraph) {
      case 'harmful':
        return 'Query Analysis';
      case 'hourly-peaks':
        return 'Risk Peaks by Hour';
      case 'daily-peaks':
        return 'Risk Peaks by Day of Week';
      case 'query-types':
        return 'Mind Analysis (Mental Health Categories)';
      case 'overall-risk':
        return 'Sentimental Score';
      case 'exposure-time':
        return 'Exposure Time on Harmful Queries';
      default:
        return 'Select a graph from the sidebar';
    }
  };

  useEffect(() => {
    if (!userDetails) {
      navigate('/');
    }
  }, [userDetails]);

  useEffect(() => {
    saveSelectedChildToStorage(selectedChild);
  }, [selectedChild]);

  useEffect(() => {
    const storedChild = getSelectedChildFromStorage();
    if (storedChild) {
      const cachedData = localStorage.getItem(`childSearchHistory_${storedChild._id || storedChild.name}`);
      if (cachedData) {
        setSearchHistories(JSON.parse(cachedData));
        processDashboardData(JSON.parse(cachedData));
      } else {
        fetchSearchHistoriesForChild(storedChild).then(data => {
          setSearchHistories(data);
        });
      }
    } else if (initialSelectedChild) {
      fetchSearchHistoriesForChild(initialSelectedChild).then(data => {
        setSearchHistories(data);
      });
    }
  }, []);

  useEffect(() => {
    const handleClickOutside = (e) => {
      if (showProfileSelector && !e.target.closest('.profile-selector-container') && 
          !e.target.closest('h2[style*="cursor: pointer"]')) {
        setShowProfileSelector(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, [showProfileSelector]);

  useEffect(() => {
    const fetchSearchHistories = async () => {
      try {
        if (!userDetails?._id) {
          return;
        }
        const currentChild = selectedChild || getSelectedChildFromStorage();
        if (!currentChild?.name) {
          setSearchHistories([]);
          return;
        }
        const url = `http://localhost:5000/api/searches/child?userId=${userDetails._id}&childName=${encodeURIComponent(currentChild.name)}`;
        const response = await fetch(url);
        const result = await response.json();
        if (result.data) {
          setSearchHistories(result.data);
          Object.keys(localStorage).forEach(key => {
            if (key.startsWith('childSearchHistory_')) {
              localStorage.removeItem(key);
            }
          });
          localStorage.setItem(
            `childSearchHistory_${currentChild._id || currentChild.name}`, 
            JSON.stringify(result.data)
          );
        } else {
          setSearchHistories([]);
        }
      } catch (err) {
        console.log('Failed to fetch search history');
      }
    };
    fetchSearchHistories();
  }, [userDetails, selectedChild]);

  const handleChildSelect = (child) => {
    setSearchHistories([]);
    setHarmfulNonHarmfulRatio({ harmful: 0, nonHarmful: 0 });
    setQueryTypeCounts([0, 0, 0, 0, 0]);
    setTopRiskCategories([]);
    setRiskPeaksByHour([]);
    setRiskPeaksByDay([]);
    setPredictionScores([]);
    setExposureTimeData([]);
    setLatestPrediction(null);
    setSelectedChild(child);
    setShowProfileSelector(false);
    fetchSearchHistoriesForChild(child).then(data => {
      setSearchHistories(data);
      setLoading(false);
    });
  };

  const fetchSearchHistoriesForChild = async (child) => {
    if (!userDetails?._id || !child?.name) return [];
    const url = `http://localhost:5000/api/searches/child?userId=${userDetails._id}&childName=${encodeURIComponent(child.name)}`;
    const response = await fetch(url);
    const result = await response.json();
    return result.data || [];
  };

  useEffect(() => {
    if (searchHistories.length > 0) {
      processDashboardData(searchHistories);
      setLoading(false); 
    } else {
      setLoading(false);
    }
  }, [searchHistories, filter, specificDate, customRange.start, customRange.end]);

  const handleGraphClick = (chartType, clickedElement) => {
    if (!clickedElement || clickedElement.length === 0) return;
    const element = clickedElement[0];
    switch(chartType) {
      case 'harmful':
        break;
      case 'hourly-peaks':
        const hour = riskPeaksByHour[element.index].hour;
        navigateToFilteredData(true, null, {
          hourStart: hour,
          hourEnd: hour
        });
        break;
      case 'daily-peaks':
        const dayIndex = element.index;
        navigateToFilteredData(true, null, {
          dayOfWeek: dayIndex
        });
        break; 
      case 'query-types':
        break;
      case 'exposure-time':
        const dateItem = exposureTimeData[element.index];
        navigateToFilteredData(true, null, {
          specificDate: dateItem.date 
        });
        break;
      case 'overall-risk':
        const scoreDate = predictionScores[element.index].date;
        navigateToFilteredData(null, null, {
          specificDate: scoreDate.toISOString().split('T')[0]
        });
        break;
    }
  };

  const processDashboardData = (data) => {
    if (!data || data.length === 0) {
      return;
    }
    const userId = effectiveUser._id?.$oid || effectiveUser._id;
    let filteredData = data.filter(h => {
      const isUserMatch = typeof h.userId === 'string' 
        ? h.userId === userId
        : h.userId?._id === userId;
      const isChildMatch = !selectedChild || 
        (h.childName && h.childName === selectedChild.name);
      return isUserMatch && isChildMatch;
    });
    filteredData = filterData(filteredData, filter);
    const harmfulItems = filteredData.filter(h => {
      const isHarmful = typeof h.isHarmful === 'string' 
        ? h.isHarmful.toLowerCase() === 'true'
        : Boolean(h.isHarmful);
      return isHarmful;
    });
    setHarmfulNonHarmfulRatio({
      harmful: harmfulItems.length,
      nonHarmful: filteredData.length - harmfulItems.length
    });
    const newQueryTypeCounts = [0, 0, 0, 0, 0]; 
    filteredData.forEach(item => {
      const prediction = (item.predictedResult || '').toLowerCase().trim();
      if (!item.isHarmful) {
        newQueryTypeCounts[4]++; 
        return;
      }
      switch(prediction) {
        case 'anxiety':
          newQueryTypeCounts[0]++;
          break;
        case 'depression':
          newQueryTypeCounts[1]++;
          break;
        case 'isolation':
          newQueryTypeCounts[2]++;
          break;
        case 'suicide':
          newQueryTypeCounts[3]++;
          break;
        default:
          newQueryTypeCounts[4]++;
      }
    });
    setQueryTypeCounts(newQueryTypeCounts);
    const dailyScores = {};
    filteredData.forEach(item => {
      if (item.isHarmful && item.sentimentScore !== undefined && item.sentimentScore !== null) {
        try {
          const date = parseCustomDate(item.dateAndTime) || new Date(item.timestamp);
          if (!date || isNaN(date.getTime())) {
            return;
          }
          const dateStr = date.toISOString().split('T')[0];
          const score = parseFloat(item.sentimentScore);
          if (!isNaN(score)) {
            if (!dailyScores[dateStr]) {
              dailyScores[dateStr] = { sum: 0, count: 0 };
            }
            dailyScores[dateStr].sum += score;
            dailyScores[dateStr].count++;
          }
        } catch (e) {
          return;
        }
      }
    });
    const formattedScores = Object.entries(dailyScores)
      .map(([dateStr, { sum, count }]) => {
        const date = new Date(dateStr);
        return {
          date: date,
          dateStr: dateStr,
          sentiment: sum / count 
        };
      })
      .filter(item => !isNaN(item.date.getTime()))
      .sort((a, b) => a.date - b.date);
    setPredictionScores(formattedScores);
    const latestPredictionValue = calculateLatestPrediction(filteredData);
    setLatestPrediction({
      predictedResult: latestPredictionValue,
      date: new Date()
    });
    const hourlyCounts = Array(24).fill(0).map((_, hour) => ({ hour, count: 0 }));
    filteredData.forEach(item => {
      if (item.isHarmful) {
        const date = parseCustomDate(item.dateAndTime) || new Date(item.timestamp);
        const hour = date.getHours();
        hourlyCounts[hour].count++;
      }
    });
    setRiskPeaksByHour(hourlyCounts);
    const days = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];
    const dailyCounts = days.map(day => ({ day, count: 0 }));
    filteredData.forEach(item => {
      if (item.isHarmful) {
        const date = parseCustomDate(item.dateAndTime) || new Date(item.timestamp);
        const dayOfWeek = date.getDay();
        dailyCounts[dayOfWeek].count++;
      }
    });
    setRiskPeaksByDay(dailyCounts);
    const riskCategories = {};
    filteredData.forEach(item => {
      if (item.isHarmful) {
        const category = item.predictedResult || 'Unknown';
        riskCategories[category] = (riskCategories[category] || 0) + 1;
      }
    });
    setTopRiskCategories(
      Object.entries(riskCategories)
        .map(([category, count]) => ({ category, count }))
        .sort((a, b) => b.count - a.count)
    );
    const exposureByDate = {};
    filteredData.forEach(item => {
      if (item.isHarmful && item.totalTimeSpent) { 
        try {
          const date = parseCustomDate(item.dateAndTime) || new Date(item.timestamp);
          if (!date || isNaN(date.getTime())) {
            return;
          }
          const dateStr = date.toISOString().split('T')[0];
          const exposureSeconds = parseFloat(item.totalTimeSpent);  
          if (!isNaN(exposureSeconds)) {
            if (!exposureByDate[dateStr]) {
              exposureByDate[dateStr] = 0;
            }
            exposureByDate[dateStr] += exposureSeconds;
          }
        } catch (e) {
          return;
        }
      }
    });
    const exposureData = Object.entries(exposureByDate)
      .map(([dateStr, time]) => {
        const date = new Date(dateStr);
        return {
          date: date,
          displayDate: date.toLocaleDateString('en-US', {
            month: 'short',
            day: 'numeric'
          }),
          time: time 
        };
      })
      .sort((a, b) => a.date - b.date);
    setExposureTimeData(exposureData);
  };

  const filterData = (data, filterType) => {
    let result = data;
    if (filterType === 'today') {
      const todayStart = new Date();
      todayStart.setHours(0, 0, 0, 0);
      const todayEnd = new Date();
      todayEnd.setHours(23, 59, 59, 999);
      result = result.filter(h => {
        try {
          const date = parseCustomDate(h.dateAndTime) || new Date(h.timestamp);
          const isToday = date >= todayStart && date <= todayEnd;
          return isToday;
        } catch (e) {
          return false;
        }
      });
    }
    else if (filterType === 'last_week') {
      const end = new Date(); 
      end.setHours(23, 59, 59, 999); 
      const start = new Date(end);
      start.setDate(end.getDate() - 6); 
      start.setHours(0, 0, 0, 0);
      result = result.filter(h => {
        const date = parseCustomDate(h.dateAndTime) || new Date(h.timestamp);
        return date >= start && date <= end;
      });
    }
    else if (filterType === 'specific_date' && specificDate) {
      const [year, month, day] = specificDate.split('-').map(Number);
      const startDate = new Date(year, month - 1, day, 0, 0, 0);
      const endDate = new Date(year, month - 1, day, 23, 59, 59, 999);
      result = result.filter(h => {
        const date = parseCustomDate(h.dateAndTime) || new Date(h.timestamp);
        return date >= startDate && date <= endDate;
      });
    }
    else if (filterType === 'custom_range' && customRange.start && customRange.end) {
      const startDate = new Date(customRange.start);
      const endDate = new Date(customRange.end);
      startDate.setHours(0, 0, 0, 0);
      endDate.setHours(23, 59, 59, 999);
      result = result.filter(h => {
        const date = parseCustomDate(h.dateAndTime) || new Date(h.timestamp);
        return date >= startDate && date <= endDate;
      });
    }
    return result;
  };

  const handleFilterChange = (e) => {
    const newFilter = e.target.value;
    setFilter(newFilter);
    if (newFilter === 'specific_date') {
      const today = new Date().toISOString().split('T')[0];
      setSpecificDate(today);
    } else if (newFilter === 'custom_range') {
      const end = new Date();
      const start = new Date();
      start.setDate(end.getDate() - 7);
      setCustomRange({
        start: formatDateForInput(start),
        end: formatDateForInput(end)
      });
    } else {
      setSpecificDate('');
      setCustomRange({ start: '', end: '' });
    }
  };

  const calculateLatestPrediction = (data) => {
    if (!data || data.length === 0) return null;
    const harmfulData = data.filter(item => {
      return typeof item.isHarmful === 'string' 
        ? item.isHarmful.toLowerCase() === 'true'
        : Boolean(item.isHarmful);
    });
    if (harmfulData.length === 0) return null;
    const end = new Date();
    end.setHours(23, 59, 59, 999);
    const start = new Date(end);
    start.setDate(end.getDate() - 6); 
    start.setHours(0, 0, 0, 0);
    const recentData = harmfulData.filter(item => {
      const itemDate = new Date(item.dateAndTime || item.timestamp);
      return itemDate >= start && itemDate <= end;
    });
    const dataToUse = recentData.length > 0 ? recentData : harmfulData;
    const predictionCounts = {};
    dataToUse.forEach(item => {
      const prediction = item.predictedResult || item.prediction;
      if (prediction) {
        predictionCounts[prediction] = (predictionCounts[prediction] || 0) + 1;
      }
    });
    let maxCount = 0;
    let latestPrediction = null;
    Object.entries(predictionCounts).forEach(([prediction, count]) => {
      if (count > maxCount || (count === maxCount && !latestPrediction)) {
        maxCount = count;
        latestPrediction = prediction;
      }
    });
    if (!latestPrediction && dataToUse.length > 0) {
      const sortedByDate = [...dataToUse].sort((a, b) => {
        const dateA = new Date(a.dateAndTime || a.timestamp);
        const dateB = new Date(b.dateAndTime || b.timestamp);
        return dateB - dateA;
      });
      latestPrediction = sortedByDate[0]?.predictedResult || sortedByDate[0]?.prediction || null;
    }
    return latestPrediction;
  };

  const pieData = {
    labels: ['Harmful Searches', 'Non-Harmful Searches'],
    datasets: [{
      data: [harmfulNonHarmfulRatio.harmful, harmfulNonHarmfulRatio.nonHarmful],
      backgroundColor: ['#dc3545', '#28a745'],
      borderColor: '#fff',
      borderWidth: 2,
      hoverOffset: 10,
    }],
  };

  const pieOptions = {
    plugins: {
      datalabels: {
        formatter: (value) => {
          const total = harmfulNonHarmfulRatio.harmful + harmfulNonHarmfulRatio.nonHarmful;
          return total ? Math.round((value / total) * 100) + '%' : '0%'; 
        },
        color: '#333',
        font: { weight: 'bold', size: 14 },
        offset: 10,
        textAlign: 'center',
      },
      legend: { 
        position: 'bottom', 
        align: 'center', 
        labels: { 
          boxWidth: 20, 
          padding: 10,
          onClick: (e, legendItem, legend) => {
            e.stopPropagation();
            const index = legendItem.datasetIndex;
            const isHarmful = index === 0;
            navigateToFilteredData(isHarmful);
          }
        } 
      },
      tooltip: {
        callbacks: {
          afterLabel: (context) => {
            return 'Click to view details';
          }
        }
      }
    },
    onClick: (event, elements) => {
      if (elements.length > 0) {
        const element = elements[0];
        const isHarmful = element.index === 0;
        navigateToFilteredData(isHarmful);
      }
    }
  };

  const generatePDFWithDateRange = async () => {
    try {
      if (!exportRange.start || !exportRange.end) {
        alert('Please select both start and end dates');
        return;
      }
      const startDate = new Date(exportRange.start);
      const endDate = new Date(exportRange.end);
      if (startDate > endDate) {
        alert('Start date must be before end date');
        return;
      }
      const filteredData = searchHistories.filter(item => {
        const itemDate = parseCustomDate(item.dateAndTime) || new Date(item.timestamp);
        return itemDate >= startDate && itemDate <= endDate;
      });
      if (filteredData.length === 0) {
        alert('No data available for the selected date range');
        return;
      }
      const originalFilter = filter;
      setFilter('custom_range');
      setCustomRange({ start: exportRange.start, end: exportRange.end });
      await new Promise(resolve => setTimeout(resolve, 500));
      const pdf = new jsPDF('p', 'mm', 'a4');
      let yPosition = 20;
      pdf.setFontSize(22);
      pdf.text(`${selectedChild?.name || 'User'}'s Dashboard Report`, 105, 40, { align: 'center' });
      pdf.setFontSize(16);
      pdf.text(`Date Range: ${exportRange.start} to ${exportRange.end}`, 105, 50, { align: 'center' });
      pdf.text(`Generated on ${new Date().toLocaleDateString()}`, 105, 60, { align: 'center' });
      pdf.addPage();
      const charts = [
        { id: 'harmful', title: 'Query Analysis' },
        { id: 'hourly-peaks', title: 'Risk Peaks by Hour' },
        { id: 'daily-peaks', title: 'Risk Peaks by Day of Week' },
        { id: 'query-types', title: 'Search Query Types' },
        { id: 'overall-risk', title: 'Overall Risk Score' },
        { id: 'exposure-time', title: 'Exposure Time' }
      ];
      for (const chart of charts) {
        setSelectedGraph(chart.id);
        await new Promise(resolve => setTimeout(resolve, 500)); 
        const chartElement = document.querySelector(`.chart-container canvas`);
        if (chartElement) {
          const imgData = chartElement.toDataURL('image/png');
          const imgWidth = 180;
          const imgHeight = (chartElement.height * imgWidth) / chartElement.width;
          pdf.setFontSize(16);
          pdf.text(chart.title, 105, yPosition, { align: 'center' });
          yPosition += 10;
          pdf.addImage(imgData, 'PNG', 15, yPosition, imgWidth, imgHeight);
          yPosition += imgHeight + 15;
          if (yPosition > 250) {
            pdf.addPage();
            yPosition = 20;
          }
        }
      }
      pdf.save(`${selectedChild?.name || 'User'}_Dashboard_Report_${exportRange.start}_to_${exportRange.end}.pdf`);
      setSelectedGraph('harmful');
      setFilter(originalFilter);
      setExportRange({...exportRange, showDialog: false});
    } catch (error) {
      alert('Failed to generate PDF report. Please try again.');
    }
  };

  const navigateToFilteredData = (isHarmful, queryType = null, additionalFilters = {}) => {
    let filtered = filterData(searchHistories, filter);
    filtered = filtered.filter(item => {
      if (isHarmful !== null) {
        const itemIsHarmful = typeof item.isHarmful === 'string' 
          ? item.isHarmful.toLowerCase() === 'true'
          : Boolean(item.isHarmful);
        if (itemIsHarmful !== isHarmful) return false;
      }
      
      if (queryType) {
        if (queryType === 'No Risk') {
          const itemIsHarmful = typeof item.isHarmful === 'string' 
            ? item.isHarmful.toLowerCase() === 'true'
            : Boolean(item.isHarmful);
          if (itemIsHarmful) return false;
        } else {
          const prediction = (item.predictedResult || '').toLowerCase();
          if (prediction !== queryType.toLowerCase()) return false;
        }
      }
      
      if (additionalFilters.hourStart !== undefined) {
        const date = parseCustomDate(item.dateAndTime) || new Date(item.timestamp);
        const hour = date.getHours();
        if (hour < additionalFilters.hourStart || hour > additionalFilters.hourEnd) {
          return false;
        }
      }
      
      if (additionalFilters.dayOfWeek !== undefined) {
        const date = parseCustomDate(item.dateAndTime) || new Date(item.timestamp);
        if (date.getDay() !== additionalFilters.dayOfWeek) {
          return false;
        }
      }
      
      if (additionalFilters.specificDate) {
        const itemDate = parseCustomDate(item.dateAndTime) || new Date(item.timestamp);
        const filterDate = new Date(additionalFilters.specificDate);
        return (
          itemDate.getFullYear() === filterDate.getFullYear() &&
          itemDate.getMonth() === filterDate.getMonth() &&
          itemDate.getDate() === filterDate.getDate()
        );
      }
      
      return true;
    });

    let title = selectedChild ? `${selectedChild.name}'s ` : '';
    if (queryType) {
      title += `${queryType} Searches`;
    } else if (additionalFilters.hourStart !== undefined) {
      const period = additionalFilters.hourStart < 12 ? 'AM' : 'PM';
      const hour12 = additionalFilters.hourStart % 12 || 12;
      title += `Searches at ${hour12}${period}`;
    } else if (additionalFilters.dayOfWeek !== undefined) {
      const days = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];
      title += `${days[additionalFilters.dayOfWeek]} Searches`;
    } else if (additionalFilters.specificDate) {
      title += `Searches on ${new Date(additionalFilters.specificDate).toLocaleDateString()}`;
    } else {
      title += isHarmful ? 'Harmful Searches' : 'Non-Harmful Searches';
    }

    const filterParams = {};
    if (filter === 'specific_date' && specificDate) {
      filterParams.specificDate = specificDate;
    } else if (filter === 'custom_range' && customRange.start && customRange.end) {
      filterParams.startDate = customRange.start;
      filterParams.endDate = customRange.end;
    } else if (filter === 'today') {
      const today = new Date().toISOString().split('T')[0];
      filterParams.specificDate = today;
    } else if (filter === 'last_week') {
      const end = new Date();
      const start = new Date();
      start.setDate(end.getDate() - 6);
      filterParams.startDate = formatDateForInput(start);
      filterParams.endDate = formatDateForInput(end);
    }

    navigate('/detailed-data', {
      state: {
        searchData: filtered,
        title,
        currentFilter: filter,
        filterParams: {
          ...filterParams,
          ...additionalFilters
        }
      }
    });
  };

  const topRiskCategoriesBarData = {
    labels: topRiskCategories.map(item => item.category),
    datasets: [{
      label: 'Frequency',
      data: topRiskCategories.map(item => item.count),
      backgroundColor: '#28a745',
    }],
  };

  const chartOptions = {
    scales: {
      y: {
        beginAtZero: true,
        title: {
          display: true,
          text: 'Count',
        },
        ticks: {
          precision: 0,
          stepSize: 1,
        },
      },
      x: {
        title: {
          display: true,
          text: 'Time',
        },
      },
    },
    plugins: {
      legend: {
        position: 'bottom',
      },
    },
  };

  const barChartOptions = {
    ...chartOptions,
    plugins: {
      legend: {
        display: false,
      },
    },
  };

  const queryTypePieData = {
    labels: QUERY_TYPE_LABELS.filter((_, index) => queryTypeCounts[index] > 0),
    datasets: [{
      data: queryTypeCounts.filter(count => count > 0),
      backgroundColor: QUERY_TYPE_COLORS.filter((_, index) => queryTypeCounts[index] > 0),
      borderColor: '#fff',
      borderWidth: 2,
    }],
  };

  const queryTypePieOptions = {
    cutout: '60%',
    plugins: {
      datalabels: {
        formatter: (value) => {
          return value > 0 ? value : '';
        },
        color: '#333',
        font: { weight: 'bold', size: 12 }
      },
      legend: { 
        position: 'bottom',
        onClick: (event, legendItem, legend) => {
          event.stopPropagation();
          const index = legendItem.datasetIndex;
          const queryType = QUERY_TYPE_LABELS[index];
          if (queryTypeCounts[index] > 0) {
            navigateToFilteredData(
              queryType !== 'No Risk',
              queryType
            );
          }
        },
        labels: {
          generateLabels: (chart) => {
            const data = chart.data;
            return data.labels.map((label, i) => ({
              text: `${label}: ${data.datasets[0].data[i]}`,
              fillStyle: data.datasets[0].backgroundColor[i],
              hidden: false,
              index: i
            }));
          }
        }
      },
      tooltip: {
        callbacks: {
          label: (context) => {
            const label = context.label || '';
            const value = context.raw || 0;
            const total = context.dataset.data.reduce((a, b) => a + b, 0);
            const percentage = Math.round((value / total) * 100);
            return `${label}: ${value} (${percentage}%)`;
          },
          afterLabel: (context) => {
            return 'Click to view details';
          }
        }
      }
    },
    onClick: (event, elements) => {
      if (elements.length > 0) {
        const index = elements[0].index;
        const queryType = QUERY_TYPE_LABELS[index];
        navigateToFilteredData(
          queryType !== 'No Risk',
          queryType
        );
      }
    }
  };

  const renderGraph = () => {
    if (loading) {
      return (
        <div className="loading-spinner">
          <i className="fas fa-circle-notch fa-spin"></i>
        </div>
      );
    }

    switch(selectedGraph) {
      case 'harmful':
        return <Pie data={pieData} options={pieOptions} />;
      case 'risk-categories':
        return <Bar data={topRiskCategoriesBarData} options={barChartOptions} />;
      case 'hourly-peaks':
        const maxCount = Math.max(...riskPeaksByHour.map(item => item.count));
        const adjustedMax = maxCount + 1;
        return (
          <Bar 
            data={{
              labels: riskPeaksByHour.map(item => {
                const hour12 = item.hour % 12 || 12;
                const ampm = item.hour < 12 ? 'AM' : 'PM';
                return `${hour12}${ampm}`;
            }),
            datasets: [{
                label: 'Search Count',
                data: riskPeaksByHour.map(item => item.count),
                backgroundColor: '#6f42c1',
              }],
            }} 
            options={{
              ...barChartOptions,
              onClick: (event, elements) => {
                handleGraphClick('hourly-peaks', elements);
              },
              scales: {
                y: {
                  beginAtZero: true,
                  max: adjustedMax,
                  ticks: {
                    precision: 0,
                    stepSize: 1,
                  }
                },
                x: {
                  title: {
                    display: true,
                    text: 'Hour',
                  }
                }
              },
              plugins: {
                ...barChartOptions.plugins,
                datalabels: {
                  anchor: 'end',
                  align: 'end',
                  color: '#444',
                  font: { weight: 'bold', size: 12 },
                  formatter: (value) => {
                    if (value === 0) return '';
                    return value;
                  },
                },
              },
            }} 
          />
        );
      case 'daily-peaks': {
        const counts = riskPeaksByDay.map(item => item.count);
        const maxCount = Math.max(...counts);
        const adjustedMax = maxCount + 1;
        return (
          <Bar
            data={{
              labels: ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'],
              datasets: [{
                label: 'Search Count',
                data: counts,
                backgroundColor: '#fd7e14',
              }],
            }}
            options={{
              ...barChartOptions,
              onClick: (event, elements) => {
                handleGraphClick('daily-peaks', elements);
              },
              scales: {
                y: {
                  beginAtZero: true,
                  max: adjustedMax,
                  ticks: {
                    precision: 0,
                    stepSize: 1,
                  }
                },
                x: {
                  title: {
                    display: true,
                    text: 'Day',
                  }
                }
              },
              plugins: {
                ...barChartOptions.plugins,
                datalabels: {
                  anchor: 'end',
                  align: 'end',
                  color: '#444',
                  font: { weight: 'bold', size: 12 },
                  formatter: (value) => {
                    if (value === 0) return '';
                    return value;
                  },
                },
              },
            }}
          />
        );
      }
      case 'query-types':
        const hasData = queryTypeCounts.some(count => count > 0);
        return hasData ? (
          <Pie data={queryTypePieData} options={queryTypePieOptions} />
        ) : (
          <div className="no-data-message">
            No query type data available for the selected period
          </div>
        );
      case 'exposure-time':
        const filteredExposureData = exposureTimeData.filter(item => {
          const itemDate = item.date;
          return !isNaN(itemDate.getTime()); 
        });
        const maxExposureValue = Math.max(...filteredExposureData.map(item => item.time), 0);
        const yAxisMax = maxExposureValue * 1.3; 
        return filteredExposureData.length > 0 ? (
          <Bar
            data={{
              labels: filteredExposureData.map(item => item.displayDate),
              datasets: [{
                label: 'Total Time Spent on Harmful Queries',
                data: filteredExposureData.map(item => item.time),
                backgroundColor: 'rgba(220, 53, 69, 0.7)',
                borderColor: 'rgba(220, 53, 69, 1)',
                borderWidth: 1
              }]
            }}
            options={{
              responsive: true,
              maintainAspectRatio: false,
              plugins: {
                legend: {
                  display: false
                },
                tooltip: {
                  callbacks: {
                    label: (context) => {
                      const seconds = context.raw;
                      const minutes = Math.floor(seconds / 60);
                      const remainingSeconds = Math.round(seconds % 60);
                      return `Total Time: ${minutes}m ${remainingSeconds}s (${seconds} seconds)`;
                    },
                    afterLabel: (context) => {
                      return 'Click bar to view detailed queries';
                    }
                  }
                },
                datalabels: {
                  display: true,
                  color: '#333',
                  anchor: 'end',
                  align: 'top',
                  offset: 2, 
                  font: {
                    weight: 'bold',
                    size: 10
                  },
                  formatter: (value) => {
                    const minutes = Math.floor(value / 60);
                    const seconds = Math.round(value % 60);
                    return `${minutes}m ${seconds}s`; 
                  }
                }
              },
              scales: {
                y: {
                  beginAtZero: true,
                  max: yAxisMax, 
                  title: {
                    display: true,
                    text: 'Time Spent (seconds)',
                    font: {
                      weight: 'bold'
                    }
                  },
                  ticks: {
                    callback: (value) => {
                      const minutes = Math.floor(value / 60);
                      const seconds = Math.round(value % 60);
                      return `${minutes}m ${seconds}s`; 
                    },
                    stepSize: Math.ceil(maxExposureValue / 5) 
                  }
                },
                x: {
                  title: {
                    display: true,
                    text: 'Date',
                    font: {
                      weight: 'bold'
                    }
                  },
                  ticks: {
                    autoSkip: false,
                    maxRotation: 45, 
                    minRotation: 0
                  }
                }
              },
              barPercentage: 0.5,  
              categoryPercentage: 0.7, 
              onClick: (event, elements) => {
                if (elements.length > 0) {
                  const clickedElement = elements[0];
                  const dateItem = filteredExposureData[clickedElement.index];
                  navigateToFilteredData(true, null, {
                    specificDate: dateItem.date.toISOString().split('T')[0]
                  });
                } 
              }
            }}
          />
        ) : (
          <div className="no-data-message">
            No harmful query data with time tracking available for the selected period
          </div>
        );
      case 'overall-risk':
        const filteredScores = predictionScores
          .filter(score => !isNaN(score.sentiment))
          .sort((a, b) => a.date - b.date);
        const chartData = {
          labels: filteredScores.map(score => 
            score.date.toLocaleDateString('en-IN', {
              weekday: 'short', 
              day: 'numeric', 
              month: 'short'
            })
          ),
          datasets: [{
            label: 'Sentiment Score',
            data: filteredScores.map(score => Math.abs(parseFloat(score.sentiment.toFixed(2)))),
            backgroundColor: filteredScores.map(score => {
              if (score.sentiment < -1.4) return '#d00000';
              if (score.sentiment <= -0.6) return '#f48c06';
              return '#ffdd00';
            }),
            borderColor: '#fff',
            borderWidth: 1
          }]
        };
        return (
          <div className="sentiment-chart-container">
            <Bar
              data={chartData}
              options={{
                responsive: true,
                onClick: (event, elements) => {
                  handleGraphClick('overall-risk', elements);
                },
                plugins: {
                  legend: { display: false },
                  tooltip: {
                    callbacks: {
                      label: (context) => {
                        const originalValue = filteredScores[context.dataIndex].sentiment;
                        let risk = '';
                        if (originalValue < -1.4) risk = 'High Risk';
                        else if (originalValue <= -0.6) risk = 'Moderate Risk';
                        else risk = 'Low Risk';
                        return `Score: ${originalValue.toFixed(2)} (${risk})`;
                      },
                      afterLabel: (context) => {
                        return 'Click to view detailed queries for this day';
                      }
                    }
                  },
                  datalabels: {
                    display: true,
                    color: '#000',
                    anchor: 'end',
                    align: 'top',
                    offset: 4,
                    font: {
                      weight: 'bold',
                      size: 10
                    },
                    formatter: (value, context) => {
                      const originalValue = filteredScores[context.dataIndex].sentiment;
                      return originalValue.toFixed(2);
                    }
                  }
                },
                scales: {
                  y: {
                    beginAtZero: true,
                    max: 2,
                    ticks: {
                      stepSize: 0.5,
                      callback: (value) => {
                        return value !== 0 ? `-${value.toFixed(1)}` : '0';
                      }
                    },
                    title: {
                      display: true,
                      text: 'Sentiment Score'
                    }
                  },
                  x: {
                    title: {
                      display: true,
                      text: 'Date'
                    }
                  }
                },
                barPercentage: 0.6,
                categoryPercentage: 0.8
              }}
            />
            <div className="risk-legend">
              <div><span style={{backgroundColor: '#d00000'}}></span> High Risk</div>
              <div><span style={{backgroundColor: '#f48c06'}}></span> Moderate Risk</div>
              <div><span style={{backgroundColor: '#ffdd00'}}></span> Low Risk</div>
            </div>
          </div>
        );
      default:
        return <div>Select a graph from the sidebar</div>;
    }
  };

  if (currentPage === 'healthcare') {
    return <HealthCareComponent setCurrentPage={setCurrentPage} />;
  }

  return (
    <div className="dashboard-container">
      <div className="nav-bar">
        <div className="logo-title">
          <img src={logo} alt="logo" className="logo" />
          <h2>SafeMind Watch</h2>
        </div>
        <div className="profile-nav">
          <span className="nav-profile-name" onClick={() => navigate('/profile')}>
            Welcome,   
            {userDetails?.name || 'User'}
          </span>
        </div>
      </div>
      <aside className="sidebar">
        <nav className="nav-links">
          <ul>
            {graphNavigation.map((item) => (
              <li
                key={item.id}
                className={selectedGraph === item.id ? 'active' : ''}
                onClick={() => {
                  if (item.id === 'allSearch') {
                    navigate('/all-search-data', { 
                      state: { 
                        parentUser: userDetails, 
                        selectedChild: selectedChild,
                        filterQueryType: null
                      }
                    });
                  } 
                  else if (item.id === 'sos-reports') {
                    navigate('/sos-reports', { 
                      state: { 
                        parentUser: userDetails, 
                        selectedChild: selectedChild 
                      }
                    });
                  }else if (item.id === 'healthcare') {
                    navigate('/healthcare', {
                      state: {
                        parentUser: userDetails,
                        selectedChild: selectedChild
                      }
                    });
                  } else {
                    setSelectedGraph(item.id);
                  }
                }}
              >
                <i className={`fas ${item.icon}`}></i> {item.name}
              </li>
            ))}
          </ul>
        </nav>
      </aside>
      <main className="main-content">
        <div className="profile-header-container">
          {selectedChild && (
            <div style={{ position: 'relative', display: 'flex', alignItems: 'center', gap: '16px', marginBottom: '20px', backgroundColor: '#f0f4f8', padding: '12px 20px', borderRadius: '10px', boxShadow: '0 2px 5px rgba(0,0,0,0.1)' }}>
              <h2 
                style={{ fontSize: '24px', margin: 0, cursor: 'pointer' }}
                onClick={() => setShowProfileSelector(!showProfileSelector)}
              >
                {selectedChild.name}'s Dashboard
                <i className={`fas fa-chevron-${showProfileSelector ? 'up' : 'down'}`} style={{ marginLeft: '10px', fontSize: '16px' }}></i>
              </h2>
              <button 
                className="export-button"
                onClick={() => setExportRange({...exportRange, showDialog: true})}
                style={{ marginLeft: 'auto', padding: '8px 16px', background: '#4a6da7', color: 'white', border: 'none', borderRadius: '4px', cursor: 'pointer' }}
              >
                <i className="fas fa-file-pdf" style={{ marginRight: '8px' }}></i> Export Report
              </button>
              {exportRange.showDialog && (
                <div className="export-dialog-overlay">
                  <div className="export-dialog">
                    <h3>Select Date Range for Export</h3>
                    <div className="date-pickers">
                      <div>
                        <label>Start Date:</label>
                        <input
                          type="date"
                          value={exportRange.start}
                          onChange={(e) => setExportRange({...exportRange, start: e.target.value})}
                        />
                      </div>
                      <div>
                        <label>End Date:</label>
                        <input
                          type="date"
                          value={exportRange.end}
                          onChange={(e) => setExportRange({...exportRange, end: e.target.value})}
                        />
                      </div>
                    </div>
                    <div className="dialog-buttons">
                      <button 
                        onClick={() => setExportRange({...exportRange, showDialog: false})}
                        className="cancel-button"
                      >
                        Cancel
                      </button>
                      <button 
                        onClick={generatePDFWithDateRange}
                        className="export-button"
                        disabled={!exportRange.start || !exportRange.end}
                      >
                        Export PDF
                      </button>
                    </div>
                  </div>
                </div>
              )}  
              {showProfileSelector && (
                <div className="profile-selector-container">
                  <div className="profile-selector">
                    <h3>Select Profile</h3>
                    <div className="profile-grid">
                      {userDetails?.children?.map(child => (
                        <div 
                          key={child._id || child.name} 
                          className="profile-option"
                          onClick={() => handleChildSelect(child)}
                        >
                          <div className="profile-icon">
                            <i className="fas fa-child"></i>
                          </div>
                          <span>{child.name}</span>
                        </div>
                      ))}
                    </div>
                  </div>
                </div>
              )}
            </div>
          )}
        </div>
        <div className="kpi-container">
          <div className="kpi-card kpi-blue">
            <div className="kpi-title">Total Searches</div>
            <div className="kpi-value">{harmfulNonHarmfulRatio.harmful + harmfulNonHarmfulRatio.nonHarmful}</div>
          </div>
          <div className="kpi-card kpi-red">
            <div className="kpi-title">Harmful Searches</div>
            <div className="kpi-value">{harmfulNonHarmfulRatio.harmful}</div>
          </div>
          <div className="kpi-card kpi-green">
            <div className="kpi-title">Non-Harmful Searches</div>
            <div className="kpi-value">{harmfulNonHarmfulRatio.nonHarmful}</div>
          </div>
          <div className="kpi-card kpi-yellow">
            <div className="kpi-title">Latest Prediction</div>
            <div className="kpi-value">
              {latestPrediction?.predictedResult 
                ? latestPrediction.predictedResult.charAt(0).toUpperCase() + latestPrediction.predictedResult.slice(1)
                : '--'}
            </div>
          </div>
        </div>

        <div className="graph-and-filters-wrapper">
          <div className="filter-bar">
            <label className="filter-label">📅 Filter by Time Period:</label>
            <select 
              value={filter} 
              onChange={handleFilterChange} 
              className="filter-select"
            >
              <option value="today">Today</option>
              <option value="last_week">Last 7 Days</option>
              <option value="specific_date">Specific Date</option>
              <option value="custom_range">Custom Range</option>
            </select>

            {filter === 'specific_date' && (
              <input
                type="date"
                className="date-picker"
                value={specificDate}
                onChange={(e) => {
                  setSpecificDate(e.target.value);
                }}
                max={new Date().toISOString().split('T')[0]}
              />
            )}

            {filter === 'custom_range' && (
              <>
                <input
                  type="date"
                  className="date-picker"
                  value={customRange.start}
                  onChange={(e) => {
                    const newStart = e.target.value;
                    setCustomRange(prev => ({
                      start: newStart,
                      end: prev.end && new Date(newStart) > new Date(prev.end) ? newStart : prev.end
                    }));
                  }}
                  max={customRange.end || new Date().toISOString().split('T')[0]}
                />
                <span>to</span>
                <input
                  type="date"
                  className="date-picker"
                  value={customRange.end}
                  onChange={(e) => setCustomRange(prev => ({
                    ...prev,
                    end: e.target.value
                  }))}
                  max={new Date().toISOString().split('T')[0]}
                />
              </>
            )}
          </div>
          <div className="graph-card graph-highlight">
            <h3 className="graph-heading">
              {selectedChild ? `${selectedChild.name}'s ${getGraphHeading()}` : getGraphHeading()}
            </h3>
            <div className="chart-container">{renderGraph()}</div>
          </div>
        </div>
      </main>
    </div>
  );
};

export default Dashboard;