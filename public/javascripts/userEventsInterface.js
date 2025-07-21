/**
 * User Events Interface JavaScript
 * Handles voting interface interactions and real-time updates
 */

class UserEventsInterface {
    constructor() {
        this.init();
    }

    init() {
        document.addEventListener('DOMContentLoaded', () => {
            this.initializeVotingInterface();
            this.initializeEventUpdates();
        });
    }

    initializeVotingInterface() {
        const sliders = document.querySelectorAll('.confidence-slider');
        
        sliders.forEach(slider => {
            const pollId = slider.dataset.pollId;
            const form = document.getElementById(`vote-form-${pollId}`);
            
            if (!form) return;

            // Update confidence value display
            slider.addEventListener('input', () => {
                this.updateConfidenceDisplay(pollId, slider.value);
                this.updateSubmitButton(form, pollId);
            });
            
            // Handle option changes
            const radioButtons = form.querySelectorAll('.option-radio');
            radioButtons.forEach(radio => {
                radio.addEventListener('change', () => this.updateSubmitButton(form, pollId));
            });
            
            // Initialize display
            this.updateConfidenceDisplay(pollId, slider.value);
            
            // Form validation
            form.addEventListener('submit', (e) => this.validateVoteForm(e, form, pollId));
        });
    }

    updateConfidenceDisplay(pollId, value) {
        const displayElement = document.getElementById(`confidence-value-${pollId}`);
        if (displayElement) {
            displayElement.textContent = value;
        }
    }

    updateSubmitButton(form, pollId) {
        const submitButton = document.getElementById(`submit-vote-${pollId}`);
        const confidenceSlider = document.getElementById(`confidence-${pollId}`);
        const selectedOption = form.querySelector('input[name="optionId"]:checked');
        
        if (selectedOption && submitButton && confidenceSlider) {
            const optionText = selectedOption.dataset.optionText;
            const confidenceValue = confidenceSlider.value;
            submitButton.textContent = `${confidenceValue} points for ${optionText}`;
            submitButton.disabled = false;
        } else if (submitButton) {
            submitButton.textContent = 'Submit Vote';
            submitButton.disabled = true;
        }
    }

    validateVoteForm(event, form, pollId) {
        const selectedOption = form.querySelector('input[name="optionId"]:checked');
        const confidenceSlider = document.getElementById(`confidence-${pollId}`);
        
        if (!selectedOption) {
            event.preventDefault();
            this.showError('Please select an option');
            return false;
        }

        if (!confidenceSlider || confidenceSlider.value < 1) {
            event.preventDefault();
            this.showError('Please set a valid confidence level');
            return false;
        }

        // Show loading state
        const submitButton = document.getElementById(`submit-vote-${pollId}`);
        if (submitButton) {
            submitButton.textContent = 'Submitting...';
            submitButton.disabled = true;
        }

        return true;
    }

    initializeEventUpdates() {
        const wsUrl = document.body.dataset.websocketUrl;
        if (wsUrl) {
            this.connectWebSocket(wsUrl);
        }
    }

    connectWebSocket(url) {
        try {
            this.websocket = new WebSocket(url);
            
            this.websocket.onopen = () => {
                console.log('WebSocket connected for event updates');
                this.showConnectionStatus(true);
            };
            
            this.websocket.onmessage = (event) => {
                this.handleWebSocketMessage(JSON.parse(event.data));
            };
            
            this.websocket.onclose = () => {
                console.log('WebSocket disconnected');
                this.showConnectionStatus(false);
            };
            
            this.websocket.onerror = (error) => {
                console.error('WebSocket error:', error);
                this.showConnectionStatus(false);
            };
        } catch (error) {
            console.error('Failed to create WebSocket connection:', error);
        }
    }

    handleWebSocketMessage(data) {
        switch (data.type) {
            case 'eventUpdate':
                this.updateEventDisplay(data.event);
                break;
            case 'pollUpdate':
                this.updatePollDisplay(data.poll);
                break;
            case 'balanceUpdate':
                this.updateBalanceDisplay(data.channelId, data.balance);
                break;
            default:
                console.log('Unknown message type:', data.type);
        }
    }

    updateEventDisplay(eventData) {
        const eventElement = document.getElementById(`current-${eventData.eventId}`);
        if (eventElement) {
            // Update event status or refresh the page for major changes
            location.reload();
        }
    }

    updatePollDisplay(pollData) {
        // Update poll odds or status
        const pollElement = document.getElementById(`vote-form-${pollData.pollId}`);
        if (pollElement) {
            // Could update odds display here
        }
    }

    updateBalanceDisplay(channelId, newBalance) {
        // Update balance displays for the channel
        const balanceElements = document.querySelectorAll(`[data-channel="${channelId}"] .balance`);
        balanceElements.forEach(element => {
            element.textContent = `Balance: ${newBalance} points`;
        });

        // Update confidence sliders max values
        const sliders = document.querySelectorAll(`[data-channel="${channelId}"] .confidence-slider`);
        sliders.forEach(slider => {
            slider.max = newBalance;
            if (parseInt(slider.value) > newBalance) {
                slider.value = newBalance;
                this.updateConfidenceDisplay(slider.dataset.pollId, newBalance);
            }
        });
    }

    showConnectionStatus(connected) {

        let statusIndicator = document.getElementById('connection-status');
        
        if (!statusIndicator) {
            const h1Element = document.getElementById('my_event_active');
            console.log(h1Element)
             if (!h1Element) return null;

            statusIndicator = document.createElement('div');
            statusIndicator.id = 'connection-status';
            statusIndicator.style.cssText = `
                display: inline-block;
                    margin-left: 15px;
                    padding: 6px 12px;
                    border-radius: 4px;
                    font-size: 14px;
                    font-weight: normal;
                    vertical-align: middle;
            `;
            h1Element.appendChild(statusIndicator);
        }

        if (connected) {
            statusIndicator.textContent = '🟢 Live Updates Connected';
            statusIndicator.style.backgroundColor = '#4CAF50';
            statusIndicator.style.color = 'white';

        } else {
            statusIndicator.textContent = '🔴 Live Updates Disconnected';
            statusIndicator.style.backgroundColor = '#f44336';
            statusIndicator.style.color = 'white';
        }
    }

    showError(message) {
        // Create or update error display
        let errorElement = document.getElementById('error-display');
        
        if (!errorElement) {
            errorElement = document.createElement('div');
            errorElement.id = 'error-display';
            errorElement.style.cssText = `
                position: fixed;
                top: 50px;
                right: 10px;
                padding: 10px 15px;
                background-color: #f44336;
                color: white;
                border-radius: 5px;
                z-index: 1001;
                max-width: 300px;
            `;
            document.body.appendChild(errorElement);
        }

        errorElement.textContent = message;
        errorElement.style.display = 'block';

        // Hide after 5 seconds
        setTimeout(() => {
            errorElement.style.display = 'none';
        }, 5000);
    }

    // Public method to manually refresh event data
    refreshEvents() {
        if (this.websocket && this.websocket.readyState === WebSocket.OPEN) {
            this.websocket.send(JSON.stringify({ type: 'requestRefresh' }));
        } else {
            location.reload();
        }
    }
}

// Initialize when the script loads
const userEventsInterface = new UserEventsInterface();

// Export for potential external use
window.UserEventsInterface = UserEventsInterface;
