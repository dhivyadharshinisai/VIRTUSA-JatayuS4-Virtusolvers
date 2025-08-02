import React, { useState, useEffect } from 'react';
import './Styles/Login.css';
import { AiFillEye, AiFillEyeInvisible } from 'react-icons/ai';
import GoogleSignIn from './GoogleSignIn.js';
import { useNavigate } from 'react-router-dom';
import axios from 'axios';
import { toast } from 'react-toastify';

const Login = ({ setUserDetails }) => {
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const [showNewPassword, setShowNewPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const [resetMode, setResetMode] = useState(false);
  const [resetEmail, setResetEmail] = useState('');
  const [otp, setOtp] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [resetStep, setResetStep] = useState(1);

  useEffect(() => {
    const user = localStorage.getItem('user');
    if (user) {
      try {
        const userData = JSON.parse(user);
        setUserDetails(userData);
        navigate('/child-profile', { state: { parentUser: userData } });
      } catch (e) {
        setError('Failed to load user data');
      }
    }
  }, [navigate, setUserDetails]);

  const handleLogin = async () => {
    if (!email || !password) {
      setError('Please fill in both email and password.');
      return;
    }

    setLoading(true);
    setError('');

    try {
      const response = await fetch('http://localhost:5000/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, password }),
      });

      const data = await response.json();
      
      if (data.success && data.user) {
        await handleLoginSuccess({
          _id: data.user._id,
          email: data.user.email,
          name: data.user.name,
          phone: data.user.phone,
          children: data.user.children || []
        });
      } else {
        setError(data.message || 'Login failed!');
      }
    } catch (err) {
      setError('Server error! Try again later.');
    } finally {
      setLoading(false);
    }
  };

  const handleLoginSuccess = async (userData) => {
    try {
      const initialUser = {
        ...userData,
        children: userData.children || []
      };
      
      localStorage.setItem("user", JSON.stringify(initialUser));
      localStorage.setItem("userId", userData._id);
      setUserDetails(initialUser);
      
      const profileResponse = await fetch(`http://localhost:5000/api/user/profile?userId=${userData._id}`);
      if (!profileResponse.ok) throw new Error('Profile fetch failed');
      
      const profile = await profileResponse.json();
      const completeUser = {
        ...initialUser,
        ...profile,
        children: profile.children || initialUser.children
      };
      
      localStorage.setItem("user", JSON.stringify(completeUser));
      setUserDetails(completeUser);
      navigate('/child-profile');
    } catch (err) {
      setError('Failed to complete login. Please try again.');
    }
  };

  const handlePasswordReset = async () => {
    setLoading(true);
    setError('');

    try {
      if (resetStep === 1) {
        if (!resetEmail.match(/^[^\s@]+@[^\s@]+\.[^\s@]+$/)) {
          throw new Error('Please enter a valid email address');
        }

        const response = await axios.post('http://localhost:5000/api/forgot-password', {
          email: resetEmail
        });

        if (!response.data.success) {
          throw new Error(response.data.message || 'Failed to send OTP');
        }

        setResetStep(2);
        toast.success('OTP sent to your email');
      } else if (resetStep === 2) {
        if (!otp.match(/^\d{6}$/)) {
          throw new Error('Please enter a valid 6-digit OTP');
        }

        const response = await axios.post('http://localhost:5000/api/verify-otp', {
          email: resetEmail,
          otp
        });

        if (!response.data.success) {
          throw new Error(response.data.message || 'OTP verification failed');
        }

        setResetStep(3);
        toast.success('OTP verified successfully');
      } else if (resetStep === 3) {
        if (newPassword.length < 6) {
          throw new Error('Password must be at least 8 characters');
        }

        if (newPassword !== confirmPassword) {
          throw new Error('Passwords do not match');
        }

        const response = await axios.post('http://localhost:5000/api/reset-password', {
          email: resetEmail,
          newPassword,
          confirmPassword
        });

        if (!response.data.success) {
          throw new Error(response.data.message || 'Password reset failed');
        }

        toast.success('Password reset successfully!');
        cancelReset();
      }
    } catch (err) {
      setError(err.response?.data?.message || err.message || 'Reset failed');
    } finally {
      setLoading(false);
    }
  };

  const handleRegisterClick = () => {
    navigate('/signup');
  };

  const handleLoginError = (errorMessage) => {
    setError(errorMessage);
  };

  const cancelReset = () => {
    setResetMode(false);
    setResetStep(1);
    setResetEmail('');
    setOtp('');
    setNewPassword('');
    setConfirmPassword('');
    setError('');
  };

  return (
    <div className="login-page">
      <div className="login-container">
        <div className="login-card">
          {!resetMode ? (
            <>
              <h1 className="login-title">Login</h1>
              
              <div className="login-form">
                <input
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder="Enter your Email"
                  className="login-input"
                />
                
                <div className="password-field">
                  <input
                    type={showPassword ? "text" : "password"}
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    placeholder="Enter your Password"
                    className="login-input"
                  />
                  <span
                    onClick={() => setShowPassword(!showPassword)}
                    className="password-toggle"
                  >
                    {showPassword ? <AiFillEyeInvisible className="eye-icon" /> : <AiFillEye className="eye-icon" />}
                  </span>
                </div>
                
                <div className="login-options">
                  <span onClick={() => setResetMode(true)} className="forgot-password">
                    Forgot password?
                  </span>
                </div>
                
                <button onClick={handleLogin} disabled={loading} className="login-button">
                  {loading ? 'Loading...' : 'Login'}
                </button>
              </div>
              
              <p className="divider">OR</p>
              
              <div className="google-auth">
                <GoogleSignIn onSuccess={handleLoginSuccess} onError={handleLoginError} />
              </div>
              
              <p className="register-prompt">
                Don't have an account?{' '}
                <span onClick={handleRegisterClick} className="register-link">
                  Register
                </span>
              </p>
            </>
          ) : (
            <div className="reset-container">
              <h1 className="login-title">
                {resetStep === 1 && 'Reset Password'}
                {resetStep === 2 && 'Verify OTP'}
                {resetStep === 3 && 'New Password'}
              </h1>
              
              <div className="login-form">
                {resetStep === 1 && (
                  <input
                    type="email"
                    value={resetEmail}
                    onChange={(e) => setResetEmail(e.target.value)}
                    placeholder="Enter your email"
                    className="login-input"
                  />
                )}
                
                {resetStep === 2 && (
                  <>
                    <input
                      type="text"
                      value={otp}
                      onChange={(e) => setOtp(e.target.value)}
                      placeholder="Enter OTP"
                      className="login-input"
                    />
                    <p className="reset-instruction">OTP sent to {resetEmail}</p>
                  </>
                )}
                
                {resetStep === 3 && (
                  <>
                    <div className="password-field">
                      <input
                        type={showNewPassword ? "text" : "password"}
                        value={newPassword}
                        onChange={(e) => setNewPassword(e.target.value)}
                        placeholder="New Password"
                        className="login-input"
                      />
                      <span
                        onClick={() => setShowNewPassword(!showNewPassword)}
                        className="password-toggle"
                      >
                        {showNewPassword ? <AiFillEyeInvisible className="eye-icon" /> : <AiFillEye className="eye-icon" />}
                      </span>
                    </div>
                    <div className="password-field">
                      <input
                        type={showConfirmPassword ? "text" : "password"}
                        value={confirmPassword}
                        onChange={(e) => setConfirmPassword(e.target.value)}
                        placeholder="Confirm New Password"
                        className="login-input"
                      />
                      <span
                        onClick={() => setShowConfirmPassword(!showConfirmPassword)}
                        className="password-toggle"
                      >
                        {showConfirmPassword ? <AiFillEyeInvisible className="eye-icon" /> : <AiFillEye className="eye-icon" />}
                      </span>
                    </div>
                  </>
                )}
                
                <button onClick={handlePasswordReset} disabled={loading} className="login-button">
                  {loading ? 'Loading...' : resetStep === 1 ? 'Send OTP' : resetStep === 2 ? 'Verify OTP' : 'Update Password'}
                </button>
                
                <button onClick={cancelReset} className="back-to-login">
                  Back to Login
                </button>
              </div>
            </div>
          )}
          
          {error && <p className="error-message">{error}</p>}
        </div>
      </div>
    </div>
  );
};

export default Login;