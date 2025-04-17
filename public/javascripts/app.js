document.addEventListener('DOMContentLoaded', function() {
  if ("WebSocket" in window) {
    console.log("WebSocket is supported by your Browser!");
  } else {
    console.log("WebSocket NOT supported by your Browser!");
    return;
  }

  // Get elements
  const messagesElement = document.getElementById('messages');
  const sendButton = document.getElementById('send');
  const messageInput = document.getElementById('message');

  // Get WebSocket URL from script attribute
  const getScriptParamUrl = function() {
    const scripts = document.getElementsByTagName('script');
    const lastScript = scripts[scripts.length-1];
    return lastScript.getAttribute('data-url');
  };

  // Send message function
  const send = function() {
    const text = messageInput.value;
    messageInput.value = "";
    connection.send(text);
  };

  // Create WebSocket connection
  const url = getScriptParamUrl();
  const connection = new WebSocket(url);

  // Disable send button until connection is established
  sendButton.disabled = true;

  // WebSocket event handlers
  connection.onopen = function() {
    // Enable send button
    sendButton.disabled = false;
    
    // Add "Connected" message
    const connectedMessage = document.createElement('li');
    connectedMessage.className = 'bg-info';
    connectedMessage.style.fontSize = '1.5em';
    connectedMessage.textContent = 'Connected';
    messagesElement.prepend(connectedMessage);
    
    // Add event listeners for sending messages
    sendButton.addEventListener('click', send);
    
    messageInput.addEventListener('keypress', function(event) {
      const keycode = event.keyCode || event.which;
      if (keycode === 13) { // Enter key
        send();
      }
    });
  };

  connection.onerror = function(error) {
    console.log('WebSocket Error ', error);
  };

  connection.onmessage = function(event) {
    try {
      // Parse the JSON message
      const data = JSON.parse(event.data);
      
      // Create message element based on source
      const messageElement = document.createElement('li');
      messageElement.style.fontSize = '1.5em';
      
      // Add styling based on message source
      if (data.source === 'youtube') {
        // YouTube message styling
        messageElement.className = 'youtube-message';
        messageElement.innerHTML = `<strong>${data.message}</strong>`;
      } else if (data.source === 'user') {
        // User message styling
        messageElement.className = 'user-message';
        messageElement.textContent = data.message;
      } else {
        // System message styling
        messageElement.className = 'system-message';
        messageElement.textContent = data.message;
      }
      
      // Add timestamp
      const timestamp = new Date(data.timestamp).toLocaleTimeString();
      const timeElement = document.createElement('span');
      timeElement.className = 'message-time';
      timeElement.style.color = '#7f8c8d';
      timeElement.style.fontSize = '0.8em';
      timeElement.style.marginLeft = '10px';
      timeElement.textContent = timestamp;
      messageElement.appendChild(timeElement);
      
      messagesElement.appendChild(messageElement);
      
      // Auto-scroll to bottom
      messagesElement.scrollTop = messagesElement.scrollHeight;
    } catch (e) {
      // Fallback for non-JSON messages
      console.log('Received non-JSON message:', event.data);
      const messageElement = document.createElement('li');
      messageElement.style.fontSize = '1.5em';
      messageElement.textContent = event.data;
      messagesElement.appendChild(messageElement);
    }
  };

  console.log("chat app is running!");
});