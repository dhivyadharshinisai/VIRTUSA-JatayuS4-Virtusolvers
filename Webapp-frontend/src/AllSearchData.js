import React, { useEffect, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import logo from './assets/logoimg.jpeg';
import './Styles/AllSearchData.css';
import jsPDF from 'jspdf';
import autoTable from 'jspdf-autotable';

const AllSearchData = ({ onLogout }) => {
  const location = useLocation();
  const navigate = useNavigate();
  const { parentUser = {}, selectedChild = null } = location.state || {};

  const [originalData, setOriginalData] = useState([]);
  const [filteredData, setFilteredData] = useState([]);
  const [filters, setFilters] = useState({
    startDate: '',
    endDate: '',
    specificDate: '',
    time: '',
    seo: '',
  });

  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!parentUser._id) {
      navigate('/dashboard');
    }
  }, [parentUser, navigate]);

  const getLastWeekData = () => {
    const oneWeekAgo = new Date();
    oneWeekAgo.setDate(oneWeekAgo.getDate() - 7);
    return originalData.filter((item) => {
      const itemDate = new Date(item.dateAndTime);
      return itemDate >= oneWeekAgo;
    });
  };

  const formatTimeSpent = (seconds) => {
    if (!seconds) return '0s';
    const mins = Math.floor(seconds / 60);
    const secs = Math.floor(seconds % 60);
    
    if (mins > 0) {
      return `${mins}m ${secs}s`;
    }
    return `${secs}s`;
  };

  const handleBackToDashboard = () => {
    navigate('/dashboard', {
      state: {
        parentUser,
        selectedChild
      }
    });
  };

  const exportToPDF = () => {
    let exportData;

    const isFilterApplied =
      filters.startDate ||
      filters.endDate ||
      filters.specificDate ||
      filters.time ||
      filters.seo;

    if (isFilterApplied && filteredData.length > 0) {
      exportData = filteredData;
    } else {
      exportData = getLastWeekData();
    }

    const doc = new jsPDF();
    doc.text('Harmful Search History Report', 14, 15);

    const tableRows = exportData.map((entry, index) => {
      const { date, time } = formatToIST(entry.dateAndTime);
      return [
        index + 1,
        entry.query,
        date,
        time,
        entry.predictedResult,
        formatTimeSpent(entry.totalTimeSpent)
      ];
    });

    autoTable(doc, {
      startY: 20,
      head: [['#', 'Query', 'Date', 'Time', 'Category', 'Time Spent']],
      body: tableRows,
      styles: { fontSize: 10 },
    });

    doc.save('Search_History.pdf');
  };    

  const fetchAllData = async () => {
    try {
      setLoading(true);
      let url = 'http://localhost:5000/api/searches/child';
      
      const params = new URLSearchParams();
      params.append('userId', parentUser._id);
      params.append('isHarmful', 'true');
      
      if (selectedChild?.name) {
        params.append('childName', selectedChild.name);
      }

      const response = await fetch(`${url}?${params.toString()}`, {
        headers: {
          'Content-Type': 'application/json'
        }
      });
      
      if (response.ok) {
        const result = await response.json();
        const data = result.data || [];
        const harmfulData = data.filter(entry => entry.isHarmful);
        setOriginalData(harmfulData);
        setFilteredData(harmfulData);
      }
    } catch (error) {
      console.error('Error fetching data:', error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (parentUser._id) {
      fetchAllData();
    }
  }, [parentUser._id, selectedChild?.name]);

  const parseTime = (timeValue) => {
    if (typeof timeValue === 'number') return timeValue;
    if (!timeValue) return 0;
    
    if (typeof timeValue === 'string') {
      if (timeValue.includes('m') || timeValue.includes('s')) {
        const parts = timeValue.split(' ');
        let totalSeconds = 0;
        parts.forEach(part => {
          if (part.includes('m')) totalSeconds += parseInt(part) * 60;
          if (part.includes('s')) totalSeconds += parseInt(part);
        });
        return totalSeconds;
      }
      return parseInt(timeValue) || 0;
    }
    return 0;
  };

  const handleFilterChange = (e) => {
    const { name, value } = e.target;
    setFilters(prev => ({
      ...prev,
      [name]: value
    }));
  };

  const applyFilters = () => {
    let filtered = [...originalData];

    const parseCustomDate = (dateStr) => {
      const [datePart] = dateStr.split(', ');
      return new Date(datePart);
    };

    if (filters.specificDate) {
      const specificDateObj = new Date(filters.specificDate);
      filtered = filtered.filter((item) => {
        const itemDate = parseCustomDate(item.dateAndTime);
        return (
          itemDate.getFullYear() === specificDateObj.getFullYear() &&
          itemDate.getMonth() === specificDateObj.getMonth() &&
          itemDate.getDate() === specificDateObj.getDate()
        );
      });
    } else if (filters.startDate && filters.endDate) {
      const start = new Date(filters.startDate);
      const end = new Date(filters.endDate);
      end.setDate(end.getDate() + 1);
      filtered = filtered.filter((item) => {
        const itemDate = parseCustomDate(item.dateAndTime);
        return itemDate >= start && itemDate <= end;
      });
    }

    if (filters.time) {
      filtered = filtered.filter((item) => {
        const seconds = parseTime(item.totalTimeSpent || '0s');
        switch (filters.time) {
          case 'lt1': return seconds < 60;
          case 'lt5': return seconds < 300;
          case 'lt10': return seconds < 600;
          case 'gt10': return seconds > 600;
          default: return true;
        }
      });
    }

    if (filters.seo) {
      filtered = filtered.filter((item) =>
        item.query.toLowerCase().includes(filters.seo.toLowerCase())
      );
    }

    setFilteredData(filtered);
  };

  const formatToIST = (dateTimeStr) => {
    if (!dateTimeStr) return { date: 'N/A', time: 'N/A' };
    
    try {
      const [datePart, timePart] = dateTimeStr.split(', ');
      const [time, period] = timePart.split(' ');
      const [hours, minutes, seconds] = time.split(':');
      
      const [year, month, day] = datePart.split('-');
      const formattedDate = `${day}/${month}/${year}`;
      
      let hours12 = parseInt(hours);
      const ampm = hours12 >= 12 ? 'PM' : 'AM';
      hours12 = hours12 % 12;
      hours12 = hours12 || 12;
      
      const formattedTime = `${hours12}:${minutes} ${ampm}`;
      
      return {
        date: formattedDate,
        time: formattedTime
      };
    } catch (e) {
      return { date: 'Invalid Date', time: 'Invalid Time' };
    }
  };

  if (loading) {
    return (
      <div className="amain-content">
        <div className="loading-spinner">
          Loading search data...
        </div>
      </div>
    );
  }

  return (
    <div className="amain-content">
      <div className="nav-bar">
        <div className="logo-title">
          <img src={logo} alt="logo" className="logo" />
          <h2>SafeMind Watch</h2>
        </div>
        <div className="nav-buttons">
          <button className="back-button" onClick={handleBackToDashboard}>
            ⬅ Back to Dashboard
          </button>
          <button className="logout-btn" onClick={onLogout}>Logout</button>
        </div>
      </div>

      <header className="adashboard-header" style={{ marginTop: '80px' }}>
        <h1 className="dashboard-title">
          {selectedChild ? `${selectedChild.name}'s Abnormal Queries` : 'Overall Search History'}
        </h1>
      </header>

      <div className="card">
        <h3 className="filter-title">🔍 Filter Search Data</h3>
        <div className="filter-container">
          <div className="filter-item">
            <label>Start Date:</label>
            <input
              type="date"
              name="startDate"
              value={filters.startDate}
              onChange={handleFilterChange}
            />
          </div>
          <div className="filter-item">
            <label>End Date:</label>
            <input
              type="date"
              name="endDate"
              value={filters.endDate}
              onChange={handleFilterChange}
            />
          </div>
          <div className="filter-item">
            <label>Specific Date:</label>
            <input
              type="date"
              name="specificDate"
              value={filters.specificDate}
              onChange={handleFilterChange}
            />
          </div>
          <div className="filter-item">
            <label>Time Spent:</label>
            <select name="time" value={filters.time} onChange={handleFilterChange}>
              <option value="">All Time</option>
              <option value="lt1">Less than 1 min</option>
              <option value="lt5">Less than 5 mins</option>
              <option value="lt10">Less than 10 mins</option>
              <option value="gt10">More than 10 mins</option>
            </select>
          </div>
          <div className="filter-item">
            <label>Search Keyword:</label>
            <input
              type="text"
              name="seo"
              placeholder="Enter keyword"
              value={filters.seo}
              onChange={handleFilterChange}
            />
          </div>
         
          <button onClick={applyFilters} className="apply-filters-button">
            Apply Filters
          </button>
          <button onClick={exportToPDF} className="export-pdf-button">
            📄 Export to PDF
          </button>
        </div>
        

        <h3 className="record-heading">📝 Harmful Search Records</h3>
        <div className="table-container">
          {filteredData.length > 0 ? (
            <table>
              <thead>
                <tr>
                  <th>#</th>
                  <th>🔎 Query</th>
                  <th>📅 Date</th>
                  <th>🕒 Time</th>
                  <th>🚨 Category</th>
                  <th>⏱️ Time Spent</th>
                </tr>
              </thead>
              <tbody>
                {filteredData.map((entry, idx) => {
                  const { date, time } = formatToIST(entry.dateAndTime);
                  return (
                    <tr key={idx} className={idx % 2 === 0 ? 'even-row' : 'odd-row'}>
                      <td>{idx + 1}</td>
                      <td>{entry.query}</td>
                      <td>{date}</td>
                      <td>{time}</td>
                      <td>{entry.predictedResult}</td>
                      <td>{formatTimeSpent(entry.totalTimeSpent)}</td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          ) : (
            <div className="no-results">
              No harmful search records found for the selected filters.
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default AllSearchData;