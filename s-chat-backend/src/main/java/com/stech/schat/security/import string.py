import string
import random

def generate_text(length=102):
    # Define the character set: alphabets, digits, and special characters
    characters = string.ascii_letters + string.digits + string.punctuation
    
    # Randomly choose characters up to the specified length
    result = ''.join(random.choice(characters) for _ in range(length))
    return result

# Example usage
line = generate_text(102)
print(line)
