// JavaScript for handling event updates via WebSocket
function showFlashMessage(message, type = 'success', reloadDelay = 5000) {
  // Create the flash message element
  const flashContainer = document.querySelector('.container.my-events-container');
  const flashDiv = document.createElement('div');
  flashDiv.className = type === 'success' ? 'success-message' : 'error-message';
  flashDiv.textContent = message;

  // Insert it at the top of the container
  if (flashContainer) {
    // Remove any existing flash messages
    const existingMessages = flashContainer.querySelectorAll('.success-message, .error-message');
    existingMessages.forEach(el => el.remove());

    // Add the new message as the first child
    flashContainer.insertBefore(flashDiv, flashContainer.firstChild);

    // Add a fade-out effect before reloading
    setTimeout(() => {
      flashDiv.style.transition = 'opacity 1s';
      flashDiv.style.opacity = '0';
    }, reloadDelay - 1000);

    // Reload the page after delay
    setTimeout(() => {
      window.location.reload();
    }, reloadDelay);
  }
}

document.addEventListener('DOMContentLoaded', function() {
  if (!("WebSocket" in window)) {
    console.log("WebSocket NOT supported by your Browser!");
    return;
  }
  
  // Get WebSocket URL from script data attribute
  const getScriptParamUrl = function() {
    const scripts = document.getElementsByTagName('script');
    const lastScript = scripts[scripts.length-1];
    return lastScript.getAttribute('data-url');
  };
  
  // Connect to WebSocket
  const url = getScriptParamUrl();
  const connection = new WebSocket(url);
  
  connection.onopen = function() {
    console.log('Events WebSocket connection established');
  };
  
  connection.onmessage = function(event) {
    // Parse the message data
    showFlashMessage('Actualización de evento, se reinicia la pagina en 5 segundos (prototipo)');
  };
  
  connection.onerror = function(error) {
    console.error('WebSocket error:', error);
  };
  
  connection.onclose = function() {
    console.log('WebSocket connection closed');
    // Try to reconnect after a short delay
    setTimeout(function() {
      window.location.reload();
    }, 5000);
  };
});