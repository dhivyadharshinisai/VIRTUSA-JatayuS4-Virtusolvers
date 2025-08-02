import os
import re
import joblib
import numpy as np
from flask import Flask, request, jsonify
from vaderSentiment.vaderSentiment import SentimentIntensityAnalyzer
from transformers import pipeline

app = Flask(__name__)
vectorizer = None
scaler = None
rf_model = None
analyzer = None
bert_classifier = None

def load_models():
    global vectorizer, scaler, rf_model, analyzer, bert_classifier
    
    print("Loading models...")
    vectorizer = joblib.load('./models/vectorizer.joblib')
    scaler = joblib.load('./models/scaler.joblib')
    rf_model = joblib.load('./models/rf_model.joblib')
    analyzer = SentimentIntensityAnalyzer()

    bert_classifier = pipeline(
        "text-classification",
        model="./models/bert_model",
        tokenizer="./models/bert_model",
        truncation=True
    )
    print("Models loaded successfully")
load_models()

def get_bert_probs(text):
    try:
        preds = bert_classifier(text)
        probs = [0] * 5
        for p in preds:
            label_idx = int(p['label'].split('_')[-1])
            probs[label_idx] = p['score']
        return probs
    except Exception as e:
        print(f"BERT prediction error: {e}")
        return [0] * 5

# Prediction endpoint
@app.route('/predict', methods=['POST'])
def predict():
    data = request.json
    query = data.get('query', '')
    user_id = data.get('userId', '')

   
    sentiment_score = analyzer.polarity_scores(query)['compound']
    sentiment_scaled = scaler.transform([[sentiment_score]])[0][0]
    
    X_text = vectorizer.transform([query]).toarray()[0]

    bert_probs = get_bert_probs(query)

    features = np.concatenate((
        X_text,              
        [sentiment_scaled],  
        bert_probs         
    )).reshape(1, -1)
    
    probas = rf_model.predict_proba(features)[0]
    prediction = np.argmax(probas)
    confidence = np.max(probas)
    
    risk_map = {
        0: "No_Risk",
        1: "Depression",
        2: "Suicide",
        3: "Isolation",
        4: "Anxiety"
    }
    
    isolation_phrases = {
        'alone', 'lonely', 'isolated', 'no friends', 'by myself', 
        'no one', 'separated', 'left out', 'abandoned', 'no family',
        'no one to talk to', 'all by myself', 'feel alone', 'feel lonely',
        'nobody cares', 'no one cares', 'no one understands', 'no one is there'
    }
    depression_phrases = {
        'sad', 'unhappy', 'miserable', 'hopeless', 'empty',
        'depressed', 'worthless', 'useless', 'tired', 'exhausted',
        'can\'t go on', 'giving up', 'life is meaningless', 'no point',
        'nothing matters', 'feel nothing', 'numb inside', 'lost all hope',
        'can\'t be happy', 'never happy'
    }
    suicide_phrases = {
        'end it all', 'kill myself', 'suicide', 'want to die',
        'end my life', 'take my life', 'end everything',
        'don\'t want to live', 'can\'t take it anymore',
        'better off dead', 'no reason to live', 'want to end it',
        'tired of living', 'can\'t go on living', 'end it now',
        'don\'t want to be here', 'wish i was dead'
    }
    anxiety_phrases = {
        'anxious', 'nervous', 'worried', 'afraid', 'panicked',
        'scared', 'fear', 'stressed', 'overwhelmed', 'panic',
        'anxiety', 'panic attack', 'can\'t breathe', 'heart racing',
        'constantly worried', 'always anxious', 'scared all the time',
        'fear of', 'scared of', 'afraid of', 'nervous about'
    }
    
    no_risk_phrases = {
        'ice cream', 'icecream', 'chocolate', 'pizza', 'food',
        'movie', 'music', 'game', 'play', 'sports', 'dark',
        'laptop', 'computer', 'phone', 'tv', 'television',
        'vegetables', 'vegetarian', 'non-vegetarian', 'food', 'drink',
        'coffee', 'tea', 'soda', 'sleep', 'bed', 'room', 'house'
    }
    
    common_objects = {
        'homework', 'work', 'job', 'school', 'college', 'class', 'exam',
        'test', 'boss', 'teacher', 'professor', 'traffic', 'rain', 'weather',
        'monday', 'alarm', 'alarm clock', 'waking up', 'morning', 'waking up early',
        'vegetables', 'broccoli', 'spinach', 'homework', 'chores', 'cleaning',
        'doing dishes', 'laundry', 'shopping', 'grocery shopping', 'cooking',
        'waking up early', 'getting up early', 'waking up', 'getting up',
        'going to work', 'going to school', 'commute', 'driving', 'public transport',
        'bus', 'train', 'subway', 'taxi', 'uber', 'lyft', 'traffic jam',
        'waiting in line', 'long lines', 'waiting', 'waiting room', 'doctor\'s office',
        'dentist', 'doctor', 'hospital', 'clinic', 'pharmacy', 'drugstore',
        'shopping mall', 'mall', 'crowds', 'loud music', 'noise', 'loud noises',
        'bright lights', 'strong smells', 'perfume', 'cologne', 'cigarette smoke',
        'cigars', 'cigarettes', 'vaping', 'vape', 'e-cigarette', 'e-cig',
        'social media', 'facebook', 'instagram', 'twitter', 'tiktok', 'snapchat',
        'youtube', 'netflix', 'hulu', 'disney+', 'disney plus', 'amazon prime',
        'prime video', 'hbo max', 'hbo', 'peacock', 'apple tv', 'apple tv+',
        'paramount+', 'paramount plus', 'peacock', 'peacock tv', 'peacock streaming',
        'streaming', 'binge watching', 'binge-watching', 'binge watch', 'binge-watch',
        'binge watching shows', 'binge-watching shows', 'binge watch shows', 'binge-watch shows'
    }
    
    query_lower = query.lower().strip()
    negation_phrases = {"won't", "wouldn't", "can't", "don't", "didn't", "not", "no longer", "never", 
                        "nothing", "nobody", "nowhere", "neither", "nor", "none", "hardly", "scarcely",
                        "barely", "no one", "n't", "cannot", "can not", "no more"}
    
    def has_negation_before_phrase(text, phrase_set):
        words = text.split()
        for i, word in enumerate(words):
            if word in phrase_set:
                for j in range(max(0, i-3), i):
                    if words[j] in negation_phrases:
                        return True
        return False
    def is_common_object_hated(text):
        words = text.split()
        if 'hate' in words:
            hate_index = words.index('hate')
            next_words = words[hate_index+1:min(hate_index+3, len(words))]
            next_phrase = ' '.join(next_words)
            if any(obj in next_phrase for obj in common_objects):
                return True
            if len(next_words) > 0 and next_words[0] in {'when', 'that', 'how', 'what'}:
                return True
                
        return False
    
    if (any(phrase in query_lower for phrase in no_risk_phrases) or 
        is_common_object_hated(query_lower)):
        if not any(phrase in query_lower for phrase in suicide_phrases):
            prediction = 0  

    if confidence < 0.7:  
        negated_depression = has_negation_before_phrase(query_lower, depression_phrases)
        negated_suicide = has_negation_before_phrase(query_lower, suicide_phrases)
        negated_isolation = has_negation_before_phrase(query_lower, isolation_phrases)
        negated_anxiety = has_negation_before_phrase(query_lower, anxiety_phrases)
        
        if negated_depression or negated_suicide or negated_isolation or negated_anxiety:
            prediction = 0  
        else:
            has_any_negation = any(neg in query_lower.split() for neg in negation_phrases)
            
            if any(phrase in query_lower for phrase in suicide_phrases):
                prediction = 2  
            elif any(phrase in query_lower for phrase in depression_phrases):
                prediction = 1  
            elif any(phrase in query_lower for phrase in isolation_phrases):
                prediction = 3  
            elif any(phrase in query_lower for phrase in anxiety_phrases):
                prediction = 4  
            if has_any_negation and prediction > 0:
                prediction = min(prediction, 1) 
    
    if len(query.strip()) < 3:
        prediction = 0  
    predicted_risk = risk_map.get(prediction, "Unknown")
    
    return jsonify({
        'userId': user_id,
        'query': query,
        'predictedRisk': predicted_risk,
        'sentimentScore': float(sentiment_score),
        'predictedLabel': int(prediction),
        'confidence': float(confidence)
    })

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5001, debug=True)