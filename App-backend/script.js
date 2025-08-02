async function resetPassword() {
    const urlParams = window.location.pathname.split('/');
    const id = urlParams[3];
    const token = urlParams[4];

    const password = document.getElementById('password').value;
    const cpassword = document.getElementById('cpassword').value;

    const res = await fetch(`https://safe-mind-watcher-backend.onrender.com/reset-password/${id}/${token}`, {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({ password, cpassword })
    });

    const data = await res.json();
    alert(data.message);
}
