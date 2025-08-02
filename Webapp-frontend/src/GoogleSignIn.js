import { GoogleLogin } from '@react-oauth/google';
import axios from 'axios';

const GoogleSignIn = ({ onSuccess, onError }) => {
  const handleSuccess = async (credentialResponse) => {
    if (!credentialResponse?.credential) {
      onError('No credential received from Google');
      return;
    }

    try {
      const response = await axios.post('http://localhost:5000/api/auth/google', {
        token: credentialResponse.credential
      });

      if (response.data?.success) {
        const userData = {
          _id: response.data.user.id,
          email: response.data.user.email,
          name: response.data.user.name,
          children: response.data.user.children || []
        };
        onSuccess(userData);
      } else {
        onError(response.data?.message || 'Authentication failed');
      }
    } catch (error) {
      onError(error.response?.data?.message || 'Google login failed');
    }
  };

  const handleError = () => {
    onError('Google login failed. Please try again.');
  };

  return (
    <div className="google-signin-button">
      <GoogleLogin
        onSuccess={handleSuccess}
        onError={handleError}
        useOneTap
        text="signin_with"
        shape="rectangular"
        size="medium"
        width="300"
      />
    </div>
  );
};

export default GoogleSignIn;