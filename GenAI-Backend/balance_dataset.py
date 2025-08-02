import pandas as pd
import random
from collections import defaultdict

print("Loading and balancing dataset...")
df = pd.read_csv('data1.csv')

def balance_dataset(df, target_col='label'):
    class_groups = defaultdict(list)
    for _, row in df.iterrows():
        class_groups[row[target_col]].append(row['text'])
    max_size = max(len(texts) for texts in class_groups.values())
    balanced_data = []
    for class_name, texts in class_groups.items():
        if len(texts) < max_size:
            sampled_texts = random.choices(texts, k=max_size)
        else:
            sampled_texts = random.sample(texts, max_size)
        balanced_data.extend([{'text': text, target_col: class_name} for text in sampled_texts])

    balanced_df = pd.DataFrame(balanced_data)
    balanced_df = balanced_df.sample(frac=1, random_state=42).reset_index(drop=True)
    
    return balanced_df
balanced_df = balance_dataset(df)

balanced_counts = balanced_df['label'].value_counts()
print(balanced_counts)

