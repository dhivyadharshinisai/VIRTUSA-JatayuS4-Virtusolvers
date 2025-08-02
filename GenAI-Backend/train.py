import os
import warnings
import pandas as pd
import numpy as np
import joblib
import torch

from sklearn.ensemble import RandomForestClassifier
from sklearn.model_selection import train_test_split
from sklearn.metrics import accuracy_score, classification_report
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.preprocessing import StandardScaler
from vaderSentiment.vaderSentiment import SentimentIntensityAnalyzer

from transformers import (
    BertTokenizer, BertForSequenceClassification,
    Trainer, TrainingArguments, pipeline
)

from datasets import Dataset

warnings.filterwarnings("ignore", category=FutureWarning)
os.environ["WANDB_DISABLED"] = "true"
os.makedirs("models", exist_ok=True)
os.makedirs("logs", exist_ok=True)

print("Loading and preprocessing data...")

label_mapping = {

    "No_Risk": 0,
    "Depression": 1,
    "Suicide": 2,
    "Isolation": 3,
    "Anxiety": 4


}

reverse_label_mapping = {v: k for k, v in label_mapping.items()}
joblib.dump(reverse_label_mapping, './models/label_mapping.joblib')


print("Loading balanced dataset...")
script_dir = os.path.dirname(os.path.abspath(__file__))
data_path = os.path.join(script_dir, "balanced_data.csv")

try:
    df = pd.read_csv(data_path, encoding='ISO-8859-1')
    df.columns = [col.strip().lower() for col in df.columns]
    required_columns = {'text', 'label'}
    if not required_columns.issubset(df.columns):
        missing = required_columns - set(df.columns)
        raise ValueError(f"Missing required columns in dataset: {missing}")
    
    print(f"Successfully loaded {len(df)} samples")
    print("\n Class Distribution:")
    print(df['label'].value_counts())
    
except Exception as e:
    print(f" Error loading dataset: {str(e)}")
    print("Please make sure 'balanced_data.csv' exists in the same directory.")
    exit(1)

label_col = None
if 'label' in df.columns:
    label_col = 'label'
elif 'class' in df.columns:
    label_col = 'class'
else:
    raise ValueError("Dataset must contain either 'label' or 'class' column")

print("🧹 Cleaning and validating data...")
df['text'] = df['text'].astype(str).str.strip()
df[label_col] = df[label_col].astype(str).str.strip()
print("\n🔍 Unique values in label column before conversion:")
print(df[label_col].value_counts())
print("\n")

if df[label_col].dtype == object:
    numeric_labels = pd.to_numeric(df[label_col], errors='coerce')
    mask = numeric_labels.isna()
    if mask.any():
        mapped_labels = df.loc[mask, label_col].map(label_mapping)
        df.loc[mask, label_col] = mapped_labels
        df[label_col].update(numeric_labels[~mask])
    else:
        df[label_col] = numeric_labels

df = df.dropna(subset=[label_col])
try:
    df[label_col] = pd.to_numeric(df[label_col], errors='raise').astype(int)
except Exception as e:
    print("Error converting labels to integers:", e)
    print("Problematic values:", df[df[label_col].isna()][label_col].unique())
    raise
valid_labels = set(range(5))  # 0 to 4
invalid_labels = ~df[label_col].isin(valid_labels)
if invalid_labels.any():
    print("Found invalid label values:", df[invalid_labels][label_col].unique())
    print("Removing rows with invalid labels...")
    df = df[~invalid_labels]

print("\n Final label distribution:")
print(df[label_col].value_counts().sort_index())
print("\n")

if len(df) < 10:
    raise ValueError(f"Not enough data after cleaning. Only {len(df)} samples remain.")

print(f"Total samples after cleaning: {len(df)}")
print(f"Samples per class after cleaning:")
print(df[label_col].value_counts().sort_index())

print("\nSplitting data into training and test sets...")
df_train, df_test = train_test_split(
    df, 
    test_size=0.2, 
    random_state=42,
    stratify=df['label']  
)
print(f"Training samples: {len(df_train)}")
print(f"Test samples: {len(df_test)}")
tokenizer = BertTokenizer.from_pretrained('bert-base-uncased')
train_encodings = tokenizer(
    df_train['text'].tolist(),
    truncation=True,
    padding='max_length',
    max_length=128,
    return_tensors='pt'
)

eval_encodings = tokenizer(
    df_test['text'].tolist(),
    truncation=True,
    padding='max_length',
    max_length=128,
    return_tensors='pt'
)

class TextClassificationDataset(torch.utils.data.Dataset):
    def __init__(self, encodings, labels):
        self.encodings = encodings
        self.labels = labels

    def __getitem__(self, idx):
        item = {key: val[idx] for key, val in self.encodings.items()}
        item['labels'] = torch.tensor(self.labels[idx], dtype=torch.long)
        return item

    def __len__(self):
        return len(self.labels)


train_dataset = TextClassificationDataset(
    train_encodings,
    df_train['label'].astype(int).tolist()
)

eval_dataset = TextClassificationDataset(
    eval_encodings,
    df_test['label'].astype(int).tolist()
)

print("Training BERT model...")
model = BertForSequenceClassification.from_pretrained(
    "bert-base-uncased", 
    num_labels=5,
    problem_type="single_label_classification"
)
import transformers
print(f"Using transformers version: {transformers.__version__}")
training_args = TrainingArguments(
    output_dir="./models/bert_checkpoints",
    overwrite_output_dir=True,
    num_train_epochs=2,
    per_device_train_batch_size=4,
    per_device_eval_batch_size=4,
    save_steps=500,
    logging_dir="./logs",
    logging_steps=10,
    learning_rate=2e-5,
    weight_decay=0.01,
    warmup_steps=100
)
def compute_metrics(p):
    """Compute accuracy for predictions vs labels."""
    predictions, labels = p
    predictions = np.argmax(predictions, axis=1)
    return {"accuracy": (predictions == labels).mean()}

print("\nDataset Info:")
print(f"Training samples: {len(train_dataset)}")
print("\nDebug - Dataset type:", type(train_dataset))
print("Debug - First item type:", type(train_dataset[0]) if len(train_dataset) > 0 else "Empty dataset")
try:
    sample = train_dataset[0]
    if isinstance(sample, dict):
        print("Sample training example:", {k: v[:2] if hasattr(v, '__getitem__') else v for k, v in sample.items() if k != 'attention_mask'})
    else:
        print("Sample training example:", sample)
except Exception as e:
    print(f"Could not print sample: {e}")

print("\nTraining configuration:")
print(f"  Batch size: {training_args.per_device_train_batch_size}")
print(f"  Epochs: {training_args.num_train_epochs}")
print(f"  Learning rate: {training_args.learning_rate}")

try:
    
    trainer = Trainer(
        model=model,
        args=training_args,
        train_dataset=train_dataset
    )
    
    print("\nTrainer initialized successfully!")
    print("   Starting training...")
    
except Exception as e:
    print("\n Error initializing trainer:")
    print(str(e))
    print("\nConsider upgrading transformers with: pip install --upgrade transformers")
    print("   Or check the documentation for version 4.53.2 compatibility.")
    raise

print("\nBERT training started...")
print("Tips to prevent laptop shutdown:")
print("   - Close other applications to free up memory")
print("   - Ensure laptop is plugged in and cooling is adequate")
print("   - Training will save checkpoints every 500 steps")

os.makedirs("./models/bert_model", exist_ok=True)

try:

    print("\nStarting training...")
    trainer.train()
    

    print("\nSaving final model...")
    trainer.save_model("./models/bert_model")
    tokenizer.save_pretrained("./models/bert_model")
    
    print("\nBERT training completed successfully!")
    print("   Model saved to: ./models/bert_model")
    
except KeyboardInterrupt:
    print("\nTraining was interrupted!")
    print("Saving model checkpoint before exiting...")
    trainer.save_model("./models/bert_model_interrupted")
    tokenizer.save_pretrained("./models/bert_model_interrupted")
    print("Checkpoint saved in ./models/bert_model_interrupted")
    
except Exception as e:
    print(f"\n Training failed: {e}")
    try:
        trainer.save_model("./models/bert_model_error")
        tokenizer.save_pretrained("./models/bert_model_error")
        print(" Partial model saved in ./models/bert_model_error")
    except Exception as save_error:
        print(f"Could not save model checkpoint: {save_error}")
    raise

if torch.cuda.is_available():
    torch.cuda.empty_cache()
    print("GPU memory cleared")

print("🔍 Training TF-IDF + RandomForest with Sentiment and BERT Probs...")

text_data = df['text'].tolist()
example_data = pd.DataFrame({'Search_Query': text_data})
analyzer = SentimentIntensityAnalyzer()
example_data['Sentiment_Score'] = example_data['Search_Query'].apply(lambda x: analyzer.polarity_scores(x)['compound'])
joblib.dump(analyzer, './models/vader_analyzer.joblib')

vectorizer = TfidfVectorizer(stop_words='english', max_features=100)
X_text = vectorizer.fit_transform(example_data['Search_Query']).toarray()
joblib.dump(vectorizer, './models/vectorizer.joblib')

scaler = StandardScaler()
scaled_sentiment = scaler.fit_transform(example_data[['Sentiment_Score']])
example_data['Sentiment_Score_Scaled'] = scaled_sentiment[:, 0]
joblib.dump(scaler, './models/scaler.joblib')
bert_classifier = pipeline(
    "text-classification",
    model=model,
    tokenizer=tokenizer,
    truncation=True
)

def extract_bert_probabilities(text):
    try:
        preds = bert_classifier(text)
        num_labels = model.config.num_labels
        probs = [0] * num_labels
        for p in preds:
            label_idx = int(p['label'].split('_')[-1])
            probs[label_idx] = p['score']
        return probs
    except:
        return [0] * model.config.num_labels

example_data['BERT_Probs'] = example_data['Search_Query'].apply(extract_bert_probabilities)
X_features = []
for i, row in example_data.iterrows():
    features = np.concatenate((
        X_text[i],
        [row['Sentiment_Score_Scaled']],
        row['BERT_Probs']
    ))
    X_features.append(features)

X = np.array(X_features)
y = df[label_col].values
X_train, X_test, y_train, y_test = train_test_split(X, y, test_size=0.2, random_state=42)

rf_model = RandomForestClassifier(
    n_estimators=50,  
    max_depth=10,    
    n_jobs=1,         
    random_state=42
)
print("🌲 Training RandomForest with reduced complexity...")
rf_model.fit(X_train, y_train)

y_pred = rf_model.predict(X_test)
print("RandomForest Accuracy:", accuracy_score(y_test, y_pred))
print(" Classification Report:\n", classification_report(y_test, y_pred))

joblib.dump(rf_model, './models/rf_model.joblib')
print(" All models trained and saved successfully.")