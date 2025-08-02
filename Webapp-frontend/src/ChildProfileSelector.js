import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import axios from 'axios';
import './Styles/ChildProfileSelector.css';

const getColorFromInitial = (char) => {
  const colors = [
    '#E50914',
    '#0080FF',
    '#00D474',
    '#FFD700',
    '#A020F0',
    '#FF4500',
    '#00CED1',
    '#FF69B4',
    '#1E90FF',
    '#32CD32'
  ];
  const index = char.toUpperCase().charCodeAt(0) % colors.length;
  return colors[index];
};

const ChildProfileSelector = ({ user }) => {
  const navigate = useNavigate();
  const [localUser, setLocalUser] = useState(null);
  const [loading, setLoading] = useState(true);
  const [showAddForm, setShowAddForm] = useState(false);
  const [newChild, setNewChild] = useState({
    name: '',
    age: '',
    profileImage: null,
    previewImage: null
  });
  const [errors, setErrors] = useState({});

  useEffect(() => {
    const checkUserData = () => {
      const storedUser = localStorage.getItem('user');
      
      if (storedUser) {
        try {
          const parsedUser = JSON.parse(storedUser);
          const effectiveUser = user || parsedUser;
          setLocalUser(effectiveUser);
        } catch (e) {
          setErrors({ message: 'Failed to load user data' });
        }
      } else if (user) {
        setLocalUser(user);
      } else {
        setErrors({ message: 'No user data available' });
      }
      setLoading(false);
    };

    checkUserData();

    const handleStorageChange = () => {
      checkUserData();
    };

    window.addEventListener('storage', handleStorageChange);
    return () => window.removeEventListener('storage', handleStorageChange);
  }, [user]);

  const handleChildClick = (child) => {
    navigate('/dashboard', { 
      state: { 
        parentUser: localUser, 
        selectedChild: child 
      } 
    });
  };

  const handleAddClick = () => {
    setShowAddForm(true);
  };

  const handleImageChange = (e) => {
    const file = e.target.files[0];
    if (file) {
      const reader = new FileReader();
      reader.onloadend = () => {
        setNewChild({
          ...newChild,
          profileImage: file,
          previewImage: reader.result
        });
      };
      reader.readAsDataURL(file);
    }
  };

  const handleInputChange = (e) => {
    const { name, value } = e.target;
    setNewChild({
      ...newChild,
      [name]: value
    });
  };

  const validateForm = () => {
    const newErrors = {};
    if (!newChild.name.trim()) {
      newErrors.name = 'Name is required';
    }
    if (!newChild.age || newChild.age < 0 || newChild.age > 18) {
      newErrors.age = 'Please enter a valid age (0-18)';
    }
    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };
 
  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!validateForm()) return;

    try {
      const formData = new FormData();
      formData.append('name', newChild.name);
      formData.append('age', newChild.age);
      if (newChild.profileImage) {
        formData.append('profileImage', newChild.profileImage);
      }

      const parentId = localUser._id;
      const response = await axios.post(
        `http://localhost:5000/api/users/${parentId}/children`,
        formData,
        {
          headers: {
            'Content-Type': 'multipart/form-data'
          }
        }
      );

      const updatedUser = response.data.user;
      setLocalUser(updatedUser);
      localStorage.setItem('user', JSON.stringify(updatedUser));

      setNewChild({
        name: '',
        age: '',
        profileImage: null,
        previewImage: null
      });
      setShowAddForm(false);
      
    } catch (error) {
      setErrors({
        submit: error.response?.data?.message || 'Failed to add child profile'
      });
    }
  };

  const handleCancel = () => {
    setShowAddForm(false);
    setNewChild({
      name: '',
      age: '',
      profileImage: null,
      previewImage: null
    });
    setErrors({});
  };

  if (loading) {
    return (
      <div className="ch-loading-screen">
        <div className="ch-spinner"></div>
      </div>
    );
  }

  if (!localUser) {
    return (
      <div className="ch-auth-screen">
        <div className="ch-auth-content">
          <h1>Session Expired</h1>
          <p>Please log in again to continue</p>
          <button 
            className="ch-button"
            onClick={() => navigate('/')}
          >
            Go to Login
          </button>
        </div>
      </div>
    );
  }

  const children = localUser.children || [];

  return (
    <div className="ch-profile-selector">
      <div className="ch-profile-header">
        <h1>Who's watching?</h1>
      </div>
      
      <div className="ch-profiles-container">
        {showAddForm ? (
          <div className="ch-add-form-container">
            <h2>Add Profile</h2>
            <form onSubmit={handleSubmit} className="ch-add-form">
              <div className="ch-form-avatar">
                <label htmlFor="profile-image" className="ch-avatar-upload">
                  {newChild.previewImage ? (
                    <img 
                      src={newChild.previewImage} 
                      alt="Preview" 
                      className="ch-profile-image" 
                    />
                  ) : (
                    <div className="ch-add-icon">
                      <div className="plus-horizontal"></div>
                      <div className="plus-vertical"></div>
                    </div>
                  )}
                  <input
                    id="profile-image"
                    type="file"
                    accept="image/*"
                    onChange={handleImageChange}
                    style={{ display: 'none' }}
                  />
                </label>
              </div>
              
              <div className="ch-form-group">
                <input
                  type="text"
                  name="name"
                  value={newChild.name}
                  onChange={handleInputChange}
                  placeholder="Child's name"
                  className={`ch-form-input ${errors.name ? 'error' : ''}`}
                />
                {errors.name && <span className="ch-error">{errors.name}</span>}
              </div>

              <div className="ch-form-group">
                <input
                  type="number"
                  name="age"
                  value={newChild.age}
                  onChange={handleInputChange}
                  placeholder="Child's age"
                  min="0"
                  max="18"
                  className={`ch-form-input ${errors.age ? 'error' : ''}`}
                />
                {errors.age && <span className="ch-error">{errors.age}</span>}
              </div>
              
              <div className="ch-form-buttons">
                <button type="button" className="ch-button secondary" onClick={handleCancel}>
                  Cancel
                </button>
                <button type="submit" className="ch-button">
                  Save
                </button>
              </div>
              {errors.submit && <div className="ch-form-error">{errors.submit}</div>}
            </form>
          </div>
        ) : (
          <div className="ch-profiles-grid">
            {children.map((child, idx) => (
              <div 
                key={idx} 
                className="ch-profile-card"
                onClick={() => handleChildClick(child)}
              >
                <div className="ch-profile-avatar">
                  {child.profileImage && child.profileImage !== '/default-profile.png' ? (
                    <img
                      src={child.profileImage}
                      alt={child.name}
                      className="ch-profile-image"
                    />
                  ) : (
                    <div
                      className="ch-profile-initial"
                      style={{ backgroundColor: getColorFromInitial(child.name?.charAt(0) || '?') }}
                    >
                      {child.name?.charAt(0)?.toUpperCase() || '?'}
                    </div>
                  )}
                </div>
                <div className="ch-profile-name">
                  {child.name || 'Unnamed Child'}
                </div>
              </div>
            ))}
            
            <div 
              className="ch-profile-card add-profile"
              onClick={handleAddClick}
            >
              <div className="ch-add-icon">
                <div className="plus-horizontal"></div>
                <div className="plus-vertical"></div>
              </div>
              <div className="ch-profile-name">Add Profile</div>
            </div>
          </div>
        )}
      </div>
      
      <div className="ch-profile-footer">
        <button 
          className="ch-button secondary"
          onClick={() => navigate('/profile')}
        >
          Manage Profiles
        </button>
      </div>
    </div>
  );
};

export default ChildProfileSelector;