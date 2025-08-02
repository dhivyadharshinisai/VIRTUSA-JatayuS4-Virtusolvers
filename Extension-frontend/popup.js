const loginBtn = document.getElementById("loginBtn");
const googleLoginBtn = document.getElementById("googleLoginBtn");
const cardContent = document.querySelector('.card-content');
function showError(message) {
  const errorMsg = document.getElementById("errorMsg");
  if (!errorMsg) return;
  errorMsg.textContent = message;
  errorMsg.style.opacity = '0';
  errorMsg.style.display = 'block';
  setTimeout(() => {
    errorMsg.style.transition = 'opacity 0.3s ease-in-out';
    errorMsg.style.opacity = '1';
  }, 10);
  setTimeout(() => {
    errorMsg.style.opacity = '0';
    setTimeout(() => errorMsg.style.display = 'none', 300);
  }, 5000);
}

async function isAuthenticated() {
  return new Promise((resolve) => {
    chrome.storage.local.get(['userId', 'token'], (result) => {
      resolve(!!(result.userId && result.token));
    });
  });
}

function createInputField(id, type, placeholder, icon) {
  return `
    <div class="form-group">
      <div class="input-container">
        <i class="${icon} input-icon"></i>
        <input 
          type="${type}" 
          id="${id}" 
          class="form-input" 
          placeholder="${placeholder}" 
          required
          autocomplete="${type === 'password' ? 'current-password' : 'email'}"
        >
        ${type === 'password' ? `
          <button type="button" id="togglePassword" class="toggle-password" aria-label="Toggle password visibility">
            <i class="far fa-eye"></i>
          </button>
        ` : ''}
      </div>
      <div class="input-underline"></div>
    </div>
  `;
}

function renderLoginForm() {
  cardContent.classList.add('loading');
  setTimeout(() => {
    cardContent.innerHTML = `
      <div class="login-form">
        <div class="login-header">
          <h2>Welcome Back</h2>
          <p>Sign in to continue to SafeMind Watch</p>
        </div>
        <div class="form-container">
          ${createInputField('email', 'email', 'Email address', 'fas fa-envelope')}
          ${createInputField('password', 'password', 'Password', 'fas fa-lock')}
          <div class="form-options">
            <a href="#" id="forgotPassword" class="forgot-password">Forgot password?</a>
          </div>
          <button id="submitLogin" class="btn btn-primary">
            <span class="btn-text">Sign In</span>
            <div class="btn-loader"></div>
          </button>
          <div class="login-divider">or</div>
          <button id="googleLogin" class="btn btn-google">
            <img src="https://www.google.com/images/branding/googleg/1x/googleg_standard_color_128dp.png" alt="Google" class="google-icon">
            <span>Continue with Google</span>
          </button>
        </div>
        <div class="back-to-home">
          <a href="#" id="backToHome">
            <i class="fas fa-arrow-left"></i> Back to home
          </a>
        </div>
        <div id="errorMsg" class="error-message" style="display:none;"></div>
      </div>
    `;
    initializeForm();
    setTimeout(() => cardContent.classList.remove('loading'), 10);
  }, 300);
}

async function handleEmailLogin() {
  const email = document.getElementById('email').value.trim();
  const password = document.getElementById('password').value.trim();
  if (!email || !password) {
    showError('Please fill in both email and password.');
    return;
  }

  try {
    const response = await fetch('http://localhost:5000/api/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, password })
    });

    const data = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(data.message || 'Login failed');

    if (data.success && data.user) {
      await Promise.all([
        new Promise(resolve => chrome.storage.local.set({ 
          user: data.user, userId: data.user._id, token: data.token 
        }, resolve)),
        new Promise(resolve => chrome.storage.sync.set({
          user: data.user, userId: data.user._id, token: data.token
        }, resolve))
      ]);
      window.location.href = 'child-profile.html';
    } else {
      showError(data.message || 'Login failed. Please try again.');
    }
  } catch (err) {
    console.error('Login error:', err);
    showError(err.message || 'An error occurred. Please try again.');
  }
}

async function handleGoogleLogin() {
  try {
    chrome.identity.getAuthToken({ interactive: true }, async (token) => {
      if (chrome.runtime.lastError || !token) {
        console.error(chrome.runtime.lastError);
        showError("Failed to get Google token");
        return;
      }
      const response = await fetch("http://localhost:5000/api/auth/google", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ idToken: token })
      });
      const data = await response.json();
      if (!data.success) throw new Error(data.message || "Google login failed");

      await Promise.all([
        new Promise(resolve => chrome.storage.local.set({ user: data.user, token: data.token }, resolve)),
        new Promise(resolve => chrome.storage.sync.set({ user: data.user, token: data.token }, resolve))
      ]);
      window.location.href = "child-profile.html";
    });
  } catch (err) {
    console.error("Google login error:", err);
    showError(err.message || "Failed to sign in with Google");
  }
}

function handleForgotPassword(e) {
  e.preventDefault();
  showError('Please contact support to reset your password.');
}

function resetToInitialView(e) {
  e.preventDefault();
  window.location.reload();
}

function handleRegisterClick(e) {
  if (e) e.preventDefault();
  alert('Registration can be done only through the website or app. Please install our app.');
}

function initializeForm() {
  const togglePassword = document.getElementById('togglePassword');
  if (togglePassword) {
    togglePassword.addEventListener('click', function() {
      const passwordInput = document.getElementById('password');
      const icon = this.querySelector('i');
      const isVisible = passwordInput.type === 'text';
      passwordInput.type = isVisible ? 'password' : 'text';
      icon.classList.toggle('fa-eye', isVisible);
      icon.classList.toggle('fa-eye-slash', !isVisible);
    });
  }

  document.querySelectorAll('.form-input').forEach(input => {
    input.addEventListener('focus', function() {
      this.parentElement.classList.add('focused');
      this.parentElement.nextElementSibling.classList.add('active');
    });
    input.addEventListener('blur', function() {
      this.parentElement.classList.remove('focused');
      if (!this.value) {
        this.parentElement.nextElementSibling.classList.remove('active');
      }
    });
    input.addEventListener('input', function() {
      if (this.value) {
        this.parentElement.nextElementSibling.classList.add('active');
      } else {
        this.parentElement.nextElementSibling.classList.remove('active');
      }
    });
  });

  const submitBtn = document.getElementById('submitLogin');
  if (submitBtn) submitBtn.addEventListener('click', handleEmailLogin);

  const googleBtn = document.getElementById('googleLogin');
  if (googleBtn) googleBtn.addEventListener('click', handleGoogleLogin);

  const forgotPassword = document.getElementById('forgotPassword');
  if (forgotPassword) forgotPassword.addEventListener('click', handleForgotPassword);

  const backToHome = document.getElementById('backToHome');
  if (backToHome) backToHome.addEventListener('click', resetToInitialView);
}

document.addEventListener('DOMContentLoaded', async () => {
  if (await isAuthenticated()) {
    window.location.href = 'child-profile.html';
    return;
  }
  if (loginBtn) loginBtn.addEventListener('click', renderLoginForm);
  if (googleLoginBtn) googleLoginBtn.addEventListener('click', handleGoogleLogin);
  const registerBtn = document.getElementById('registerBtn');
  if (registerBtn) registerBtn.addEventListener('click', handleRegisterClick);
});
