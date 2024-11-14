import torch


class CNNClassifier1(torch.nn.Module):
    def __init__(self, vocab_size, embedding_dim, hidden_dim, num_layers=4, kernel_size=5, kernel_size2=3,
                 num_classes=12, dropout=0.5, stride=1):
        super(CNNClassifier1, self).__init__()
        self.emb = torch.nn.Embedding(vocab_size, embedding_dim)
        padding = (kernel_size - 1) // 2
        padding2 = (kernel_size2 - 1) // 2
        self.conv1 = torch.nn.Conv1d(embedding_dim, hidden_dim, kernel_size, padding=padding)
        self.conv2 = torch.nn.Conv1d(embedding_dim, hidden_dim, kernel_size2, padding=padding2)
        self.dropout = torch.nn.Dropout(dropout)
        self.cnn_list = list()
        for _ in range(num_layers):
            self.cnn_list.append(
                torch.nn.Conv1d(hidden_dim * 2, hidden_dim * 2, kernel_size=kernel_size, padding=padding,
                                stride=stride))
        self.cnn = torch.nn.Sequential(*self.cnn_list)
        self.conv3 = torch.nn.Conv1d(hidden_dim * 2, 256, 5, padding=2)
        self.fc = torch.nn.Linear(256, num_classes)

    def forward(self, x):
        x = self.emb(x)
        # add batch dimension
        x = x.unsqueeze(1)
        # reshape dimensions
        x = torch.permute(x, [1, 0, 2])
        x = self.dropout(x).transpose(1, 2)
        x = torch.nn.functional.relu(torch.cat((self.conv1(x), self.conv2(x)), dim=1))
        x = self.dropout(x)
        for cnn_layer in self.cnn_list:
            x = torch.nn.functional.relu(cnn_layer(x))
            x = self.dropout(x)

        x = torch.nn.functional.relu(self.conv3(x))
        x = x.transpose(1, 2)
        x_logit = self.fc(x)

        if not self.training:
            x_logit = x_logit.transpose(2, 0)
            out = torch.nn.functional.log_softmax(x_logit, dim=0).transpose(2, 0)
            out = out.squeeze(0)
        else:
            out = x_logit.squeeze(0)

        return out
