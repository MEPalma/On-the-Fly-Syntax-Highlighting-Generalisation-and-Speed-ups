import torch


class LSTMClassifier1(torch.nn.Module):

    def __init__(self, embedding_dim, hidden_dim, vocab_size, tagset_size, num_lstm_layers, is_bidirectional):
        super(LSTMClassifier1, self).__init__()
        #
        self.embedding_dim = embedding_dim
        self.hidden_dim = hidden_dim
        self.vocab_size = vocab_size
        self.tagset_size = tagset_size
        self.num_lstm_layers = num_lstm_layers
        self.is_bidirectional = is_bidirectional
        #
        self.word_embeddings = torch.nn.Embedding(vocab_size, embedding_dim) if embedding_dim > 1 else None
        self.lstm1 = torch.nn.LSTM(embedding_dim, hidden_dim, num_layers=num_lstm_layers,
                                   bidirectional=is_bidirectional)
        self.fc1 = torch.nn.Linear(hidden_dim * 2 if is_bidirectional else hidden_dim, tagset_size)

    def forward(self, seq):
        n = len(seq)
        out = self.word_embeddings(seq) if self.word_embeddings is not None else seq.float()
        out = out.view(n, 1, -1)
        out, _ = self.lstm1(out)
        if self.is_bidirectional:
            out = out[:, -1, :]
        else:
            out = out.view(n, -1)
        out = self.fc1(out)
        if not self.training:
            out = torch.nn.functional.log_softmax(out, dim=1)
        return out


class GRUClassifier1(torch.nn.Module):

    def __init__(self, embedding_dim, hidden_dim, vocab_size, tagset_size, num_gru_layers, is_bidirectional):
        super(GRUClassifier1, self).__init__()
        #
        self.embedding_dim = embedding_dim
        self.hidden_dim = hidden_dim
        self.vocab_size = vocab_size
        self.tagset_size = tagset_size
        self.num_gru_layers = num_gru_layers
        self.is_bidirectional = is_bidirectional
        #
        self.word_embeddings = torch.nn.Embedding(vocab_size, embedding_dim) if embedding_dim > 1 else None
        self.gru1 = torch.nn.GRU(embedding_dim, hidden_dim, num_layers=num_gru_layers, bidirectional=is_bidirectional)
        self.fc1 = torch.nn.Linear(hidden_dim * 2 if is_bidirectional else hidden_dim, tagset_size)

    def forward(self, seq):
        n = len(seq)
        out = self.word_embeddings(seq) if self.word_embeddings is not None else seq.float()
        out = out.view(n, 1, -1)
        out, _ = self.gru1(out)
        if self.is_bidirectional:
            out = out[:, -1, :]
        else:
            out = out.view(n, -1)
        out = self.fc1(out)
        if not self.training:
            out = torch.nn.functional.log_softmax(out, dim=1)
        return out


class RNNClassifier1(torch.nn.Module):

    def __init__(self, embedding_dim, hidden_dim, vocab_size, tagset_size, num_layers, is_bidirectional):
        super().__init__()
        self.embedding_dim = embedding_dim
        self.hidden_dim = hidden_dim
        self.vocab_size = vocab_size
        self.tagset_size = tagset_size
        self.num_layers = num_layers
        self.is_bidirectional = is_bidirectional
        #
        self.word_embeddings = torch.nn.Embedding(vocab_size, embedding_dim) if embedding_dim > 1 else None
        self.rnn = torch.nn.RNN(embedding_dim, hidden_dim, num_layers=num_layers, bidirectional=is_bidirectional)
        self.fc1 = torch.nn.Linear(hidden_dim * 2 if is_bidirectional else hidden_dim, tagset_size)

    def forward(self, seq):
        n = len(seq)
        out = self.word_embeddings(seq) if self.word_embeddings is not None else seq.float()
        out = out.view(n, 1, -1)
        out, _ = self.rnn(out)
        if self.is_bidirectional:
            out = out[:, -1, :]
        else:
            out = out.view(n, -1)
        out = self.fc1(out)
        if not self.training:
            out = torch.nn.functional.log_softmax(out, dim=1)
        return out


class CNNClassifier2(torch.nn.Module):
    def __init__(self, embedding_dim, hidden_dim, vocab_size, max_input_len, tagset_size, enc_kernel_size,
                 enc_num_layers, dec_kernel_size, dec_num_layers, dropout_p, num_heads):
        self.encoder = CNNEncoder(embedding_dim, hidden_dim, vocab_size, max_input_len, enc_kernel_size, enc_num_layers,
                                  dropout_p)
        self.decoder = CNNEDecoder(embedding_dim, hidden_dim, vocab_size, tagset_size, dec_kernel_size, dec_num_layers,
                                   num_heads)

    def forward(self, seq):
        z = self.encoder(seq)
        return self.decoder(z)


class CNNEncoder(torch.nn.Module):
    def __init__(self, embedding_dim, hidden_dim, vocab_size, max_input_len, enc_kernel_size,
                 enc_num_layers, dropout_p):
        super().__init__()
        self.enc_kernel_size = enc_kernel_size
        self.enc_num_layers = enc_num_layers

        # self.dec_kernel_size = dec_kernel_size
        # self.dec_num_layers = dec_num_layers

        # 3.1 Position Embeddings
        #   embed input elements w = (w1,...,wm)
        self.token_embedding = torch.nn.Embedding(vocab_size, embedding_dim)
        #   embedding the absolute position of input elements p = (p1,...,pm)
        self.position_embedding = torch.nn.Embedding(max_input_len, embedding_dim)

        # 3.2 Convolutional Block Structure
        #   linear mappings to project between the embedding and the convolutional outputs
        self.emb2hidden = torch.nn.Linear(embedding_dim, hidden_dim)

        # set of [enc_num_layers] convolutional layers
        self.convolutional_layers = torch.nn.ModuleList(
            [torch.nn.Conv1d(
                in_channels=hidden_dim,
                out_channels=2 * hidden_dim,
                kernel_size=enc_kernel_size,
                padding=enc_kernel_size - 1,
                padding_mode='zeros')
                for i in range(enc_num_layers)])

        # 3.3 Multi-step Attention
        #   see decoder

        # 3.4 Normalization
        #   Multiply sum of input and output of a block by sqrt(0.5)
        self.scale = torch.sqrt(torch.FloatTensor([0.5]))

        # 3.5 Initialization
        #   Apply dropout to the input of some layers
        self.dropout = torch.nn.Dropout(dropout_p)

    def forward(self, seq):
        token_emb = self.token_embedding(seq)
        pos_emb = 0  # ??
        embedded = torch.stack(token_emb, pos_emb)
        # 3.2 add lin. transformation
        inp = self.emb2hidden(embedded)

        for convLayer in self.convolutional_layers:
            # apply dropout to input?
            out = self.dropout(inp)
            # run through conv. layer
            out = convLayer(out)
            # GLU as activation function ?
            out = torch.nn.functional.glu(out)

            # add residual connection ??
            out = torch.stack(out, inp)
            # apply scaling
            out = out * self.scale

            # input of next layer = output of this layer
            inp = out

        return out


class CNNEDecoder(torch.nn.Module):
    def __init__(self, embedding_dim, hidden_dim, vocab_size, tagset_size, dec_kernel_size, dec_num_layers, dropout_p,
                 num_heads):
        self.embedding_dim = embedding_dim
        # 3.1 Position Embeddings
        #   embed input elements w = (w1,...,wm)
        self.token_embedding = torch.nn.Embedding(vocab_size, embedding_dim)
        #   embedding the absolute position of input elements p = (p1,...,pm)
        # self.position_embedding = torch.nn.Embedding(max_input_len, embedding_dim)

        # 3.2 Convolutional Block Structure
        #   linear mappings to project between the embedding and the convolutional outputs
        self.emb2hidden = torch.nn.Linear(embedding_dim, hidden_dim)

        # set of [enc_num_layers] convolutional layers
        self.convolutional_layers = torch.nn.ModuleList(
            [torch.nn.Conv1d(
                in_channels=hidden_dim,
                out_channels=2 * hidden_dim,
                kernel_size=dec_kernel_size,
                padding=dec_kernel_size - 1,  # ????
                padding_mode='zeros')
                for i in range(dec_num_layers)])

        # 3.3 Multi-step Attention
        self.multihead_attn = torch.nn.MultiheadAttention(embedding_dim, num_heads)
        #   add attention here

        self.dropout = torch.nn.Dropout(dropout_p)

        self.fc1 = torch.nn.Linear(hidden_dim, tagset_size)

    def multi_step_attention(self, inp):
        attention = 0
        return attention

    def forward(self, seq, last_attn: torch.Tensor):

        # input??
        # Initialize previous attention (alignment) to zeros
        #if last_attn is None:
        #    last_attn = value.new_zeros(batch_size, seq_len)

        inp = 0
        for conv_layer in self.convolutional_layers:
            out = self.dropout(inp)
            out = conv_layer(out)
            out = torch.nn.functional.glu(out)
            # attention ???
            query, key, value = 0, 0, 0
            attn_output, attn_weights = self.multihead_attn(query, key, value)

            # here ??

        out = self.fc1(out)
        if not self.training:
            out = torch.nn.functional.log_softmax(out, dim=1)
        return out
